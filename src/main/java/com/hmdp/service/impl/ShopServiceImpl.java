package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.entity.Voucher;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisData;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    /*
    线程池
     */
    public static final ExecutorService CACHE_REBUILD_EXECUTOR= Executors.newFixedThreadPool(10);

    @Resource
    private CacheClient cacheClient;
//    @Override
//    public Result queryById(Long id) {
////        缓存穿透
//        Shop shop = cacheClient.queryWithPassThrough(CACHE_SHOP_KEY, id, Shop.class, this::getById, CACHE_SHOP_TTL, TimeUnit.MINUTES);
//        return Result.ok(shop);
//    }
    //使用封装的工具类解决缓存穿透

    //使用封装工具类解决逻辑过期
        @Override
    public Result queryById(Long id) {
//        缓存穿透
        Shop shop = cacheClient.queryWithLogicalExpire(CACHE_SHOP_KEY, id,CACHE_SHOP_TTL, TimeUnit.MINUTES, Shop.class, this::getById );
        if (shop==null){
            return Result.fail("店铺不存在");
        }
        return Result.ok(shop);
    }
/*



    //一般代码方法
    @Override
    public Result queryById(Long id) {
//        //缓存穿透
//        Shop shop = queryWithPassThrough(id);
        //用互斥锁解决缓存击穿
//        Shop shop = queryWithMutexPassThrough(id);
//        if (shop==null){
//            Result.fail("店铺不存在");
//        }


        //逻辑过期解决缓存击穿问题
        Shop shop = queryWithLogicalExpire(id);
        if (shop==null){
            Result.fail("店铺不存在");
        }
        return Result.ok(shop);
    }

 */
    //缓存穿透
    public Shop queryWithPassThrough(Long id){
        String key=CACHE_SHOP_KEY + id;
        //从redis查询商铺缓存
        String shopjson = stringRedisTemplate.opsForValue().get(key);
//        判断是否存在
        if (StrUtil.isNotBlank(shopjson)) {
//        存在直接返回
            Shop shop = JSONUtil.toBean(shopjson, Shop.class);
            return shop;
        }
        //判断是否是空值
        if (shopjson!=null){
            return null;
        }
//        不存在，根据id查询数据库
        Shop shop=getById(id);
//        不存在返回醋五
        if (shop==null){
            //将空值写入redis
            stringRedisTemplate.opsForValue().set(key,"",CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }
//        存在写入redis
        stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(shop),CACHE_SHOP_TTL, TimeUnit.MINUTES);
//        返回

        return shop;
    }
    public Shop queryWithMutexPassThrough(Long id){
        String key=CACHE_SHOP_KEY + id;
        Shop shop=null;
        //从redis查询商铺缓存
        String shopjson = stringRedisTemplate.opsForValue().get(key);
//        判断是否存在
        if (StrUtil.isNotBlank(shopjson)) {
//        存在直接返回
            return JSONUtil.toBean(shopjson, Shop.class);
        }
        //判断是否是空值
        if (shopjson!=null){
            return null;
        }
        //实现缓存重建
        //获取互斥锁
        String lockkey=LOCK_SHOP_KEY+id;
        try {
            boolean isLock = tryLock(lockkey);
//        判断是否成功
            if (!isLock){
                //        失败则休眠并重试

                Thread.sleep(50);
                return  queryWithMutexPassThrough(id);
            }

//        成功，根据id查询数据库
            shop=getById(id);
//        不存在返回醋五
            if (shop==null){
                //将空值写入redis
                stringRedisTemplate.opsForValue().set(key,"",CACHE_NULL_TTL, TimeUnit.MINUTES);
                return null;
            }
//        存在写入redis
            stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(shop),CACHE_SHOP_TTL, TimeUnit.MINUTES);

        }catch (InterruptedException e){
            throw  new RuntimeException(e);
        }finally {
            //        释放互斥锁
            unLock(lockkey);
        }

        //        返回

        return shop;
    }

    //尝试获取锁
    private boolean tryLock(String key){
        Boolean aBoolean = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.MILLISECONDS);
        return BooleanUtil.isTrue(aBoolean);
    }
//释放锁
    private void unLock(String key){
        stringRedisTemplate.delete(key);
    }
    //逻辑过期
    public Shop queryWithLogicalExpire(Long id){
        String key=CACHE_SHOP_KEY + id;
        //从redis查询商铺缓存
        String shopjson = stringRedisTemplate.opsForValue().get(key);
//        判断是否存在
        if (StrUtil.isBlank(shopjson)) {
//        存在直接返回
            return null;
        }
        //命中需要先把json反序列化为对象
        RedisData redisData = JSONUtil.toBean(shopjson, RedisData.class);
        JSONObject data = (JSONObject) redisData.getData();
        //jsonobject是可以用utils转的
        Shop shop = JSONUtil.toBean(data, Shop.class);
        LocalDateTime expireTime = redisData.getExpireTime();
//        判断是否过期
        if (expireTime.isAfter(LocalDateTime.now())){
//        未过期直接返回
            return shop;
        }
//        已经过期  缓存重建
//        获取互斥锁
        String locKey=LOCK_SHOP_KEY+id;
        boolean isLock= tryLock(locKey);
//        判断是否成功
        if (isLock){
            //        成功，开启独立线程，实现缓存重建
            //注意获取成功后应该再次检查redis缓存是否过期
            CACHE_REBUILD_EXECUTOR.submit(()->{
                try {
                    this.saveShop2Redis(id,20L);
                }catch (Exception e){
                    throw new RuntimeException(e);
                }finally {
                    //释放锁
                    unLock(locKey);
                }

            });

        }
//        返回过期商铺信息
        return shop;



    }

    public void saveShop2Redis(Long id,Long expireSeconds) throws InterruptedException {
        //1查询店铺数据
        Shop shop=getById(id);
        Thread.sleep(200);
//        查询逻辑过期时间
        RedisData redisData = new RedisData();
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
//        写入redis

        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY+id,JSONUtil.toJsonStr(redisData));
    }
    @Override
    @Transactional//事务操作
    public Result update(Shop shop) {
        Long id=shop.getId();
        if(id==null){
            return Result.fail("店铺id不能为空");
        }
        //更新数据库
        updateById(shop);
//        删除缓存
        stringRedisTemplate.delete(CACHE_SHOP_KEY+shop.getId());
        return Result.ok();
    }
}
