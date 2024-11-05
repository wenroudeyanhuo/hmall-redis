package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private ISeckillVoucherService seckillVoucherService;
    @Resource
    private RedisIdWorker redisIdWorker;
    private BlockingQueue<VoucherOrder> orderTasks=new ArrayBlockingQueue<>(1024*1024);
    private static final ExecutorService SECKILL_ORDER_EXECUTOR= Executors.newSingleThreadExecutor();
    //在当前类初始化之后会运行这个
    @PostConstruct
    private void init(){
        SECKILL_ORDER_EXECUTOR.submit(new VoucherOrderHandler());
    }
    private class VoucherOrderHandler implements Runnable{
        @Override
        public void run() {
            while (true){
//                获取队列中的订单信息
                try {
                    VoucherOrder take = orderTasks.take();
//                创建爱你订单
                }catch (Exception e){
                    log.error("处理订单异常",e);
                }

            }
        }
    }

    @Resource
    private RedissonClient redissonClient;
    private  static final DefaultRedisScript<Long> SECKILL_SCRIPT;
    static {
        SECKILL_SCRIPT =new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));//设置脚本位置  new ClassPathResource会直接在resources下面找
        SECKILL_SCRIPT.setResultType(Long.class);//设置返回值
    }
   /* @Override
    public Result seckillVoucher(Long voucherId) {
//        查询优惠券
        SeckillVoucher voucher= seckillVoucherService.getById(voucherId);
//        判断秒杀是否开始
        if (voucher.getBeginTime().isAfter(LocalDateTime.now())) {
            return  Result.fail("秒杀尚未开始");
        }
//        判断是否结束
        if (voucher.getEndTime().isBefore(LocalDateTime.now())) {
            return  Result.fail("秒杀已经结束咧");
        }
//        判断库存是否充足
        if (voucher.getStock()<1) {
//        库存不足
            return Result.fail("库存不足");
        }
//        先拿锁再下单
        Long id = UserHolder.getUser().getId();
//        synchronized (id.toString().intern()) {
////            这里需要拿代理对象
//            IVoucherOrderService o = (IVoucherOrderService) AopContext.currentProxy();
//            return o.CreateVoucherOrder(voucherId);
//    }
        //换种方式
//        SimpleRedisLock simpleRedisLock = new SimpleRedisLock("order:" + id, stringRedisTemplate);
        RLock lock = redissonClient.getLock("lock:order:" + id);
//        获取锁
        boolean isLock =lock.tryLock();
//判断是否获取成功
        if(!isLock){
            return  Result.fail("不允许重复下单");
        }
        try{
            IVoucherOrderService o = (IVoucherOrderService) AopContext.currentProxy();
            return o.CreateVoucherOrder(voucherId);
        }finally {
            //释放锁
            lock.unlock();;
        }
    }

    */
    @Override
    public Result seckillVoucher(Long voucherId) {
//        查询优惠券
        SeckillVoucher voucher= seckillVoucherService.getById(voucherId);
//        判断秒杀是否开始
        if (voucher.getBeginTime().isAfter(LocalDateTime.now())) {
            return  Result.fail("秒杀尚未开始");
        }
//        判断是否结束
        if (voucher.getEndTime().isBefore(LocalDateTime.now())) {
            return  Result.fail("秒杀已经结束咧");
        }
//        判断库存是否充足
        if (voucher.getStock()<1) {
//        库存不足
            return Result.fail("库存不足");
        }
//        先拿锁再下单
        Long id = UserHolder.getUser().getId();
//        synchronized (id.toString().intern()) {
////            这里需要拿代理对象
//            IVoucherOrderService o = (IVoucherOrderService) AopContext.currentProxy();
//            return o.CreateVoucherOrder(voucherId);
//    }
        //换种方式
//        SimpleRedisLock simpleRedisLock = new SimpleRedisLock("order:" + id, stringRedisTemplate);
        RLock lock = redissonClient.getLock("lock:order:" + id);
//        获取锁
        boolean isLock =lock.tryLock();
//判断是否获取成功
        if(!isLock){
            return  Result.fail("不允许重复下单");
        }
        try{
            IVoucherOrderService o = (IVoucherOrderService) AopContext.currentProxy();
            return o.CreateVoucherOrder(voucherId);
        }finally {
            //释放锁
            lock.unlock();;
        }
    }
    @Transactional
    public Result CreateVoucherOrder(Long voucherId) {
//       获取用户
        Long id = UserHolder.getUser().getId();
//执行lua脚本
        Long result = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(),
                id.toString()
        );
//        判断结果是否为0
        int r = result.intValue();
        if (r!=0){
            return Result.fail(r==1 ? "库存不足":"不能重复下单");
        }
//        不为0代表没有购买资格
//        为0，又购买资格，把下单信息保存到阻塞队列
        long orderId=redisIdWorker.nextId("order");
        VoucherOrder voucherOrder=new VoucherOrder();
        voucherOrder.setId(orderId);
        voucherOrder.setUserId(id);
        voucherOrder.setVoucherId(voucherId);
//        创建阻塞队列
//        返回订单id
        return Result.ok(orderId);
    }
}
