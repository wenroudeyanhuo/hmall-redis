package com.hmdp.utils;


import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.entity.Shop;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.hmdp.utils.RedisConstants.*;

/**
 * @author 温柔的烟火
 * @date 2024/10/31-11:13
 */
@Slf4j
@Component
public class CacheClient {
    private StringRedisTemplate stringRedisTemplate;
    //spring 推荐使用构造器注入

    public CacheClient(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }
    public void  set(String key, Object value, Long time, TimeUnit unit){
        //将 value 序列化为子符串
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value),time,unit);
    }
    public void  setWithLogicalExpire(String key, Object value, Long time, TimeUnit unit){
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }
    //缓存穿透
    //Function<ID,R>  ID是参数，R是返回值
    public <R,ID> R queryWithPassThrough(String keyPrefix, ID id, Class<R> type, Function<ID,R> dbFallback,Long time,TimeUnit unit){
        String key=keyPrefix + id;
        //从redis查询缓存
        String json = stringRedisTemplate.opsForValue().get(key);
//        判断是否存在
        if (StrUtil.isNotBlank(json)) {
//        存在直接返回
            return JSONUtil.toBean(json,type);
        }
        //判断是否是空值
        if (json!=null){
            return null;
        }
//        不存在，根据id查询数据库
        R r=dbFallback.apply(id);
//        不存在返回醋五
        if (r==null){
            //将空值写入redis
            stringRedisTemplate.opsForValue().set(key,"",CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }
//        存在写入redis
        this.set(key,r,time,unit);
//        返回

        return r;
    }
//    线程池
    public static final ExecutorService CACHE_REBUILD_EXECUTOR= Executors.newFixedThreadPool(10);
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
    public <R,ID> R queryWithLogicalExpire(String KeyPrefix,ID id,Long time,TimeUnit unit,Class<R> type,Function<ID,R>dbFallback){
        String key=KeyPrefix + id;
        //从redis查询商铺缓存
        String json = stringRedisTemplate.opsForValue().get(key);
//        判断是否存在
        if (StrUtil.isBlank(json)) {
//        存在直接返回
            return null;
        }
        //命中需要先把json反序列化为对象
        RedisData redisData = JSONUtil.toBean(json,RedisData.class);
        //jsonobject是可以用utils转的
        R r= JSONUtil.toBean((JSONObject) redisData.getData(),type);
        LocalDateTime expireTime = redisData.getExpireTime();
//        判断是否过期
        if (expireTime.isAfter(LocalDateTime.now())){
//        未过期直接返回
            return r;
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
                    //查询数据库
                    R r1 = dbFallback.apply(id);

                    //写入redis
                    this.setWithLogicalExpire(key,r1,time,unit);

                }catch (Exception e){
                    throw new RuntimeException(e);
                }finally {
                    //释放锁
                    unLock(locKey);
                }

            });

        }
//        返回过期商铺信息
        return r;
    }
}
