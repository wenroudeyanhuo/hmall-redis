package com.hmdp.utils;

import cn.hutool.core.lang.UUID;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

/**
 * @author 温柔的烟火
 * @date 2024/11/4-19:36
 */
public class SimpleRedisLock implements ILock {
    private String name;
    private StringRedisTemplate stringRedisTemplate;
    private  static final DefaultRedisScript<Long> UNLOCK_SCRIPT;
    static {
        UNLOCK_SCRIPT=new DefaultRedisScript<>();
        UNLOCK_SCRIPT.setLocation(new ClassPathResource("unlock.lua"));//设置脚本位置  new ClassPathResource会直接在resources下面找
        UNLOCK_SCRIPT.setResultType(Long.class);//设置返回值
    }
    public SimpleRedisLock(String name, StringRedisTemplate stringRedisTemplate) {
        this.name = name;
        this.stringRedisTemplate = stringRedisTemplate;
    }
    //这个uuid创建方法会去掉横线
    private static final String ID_PREFIX= UUID.fastUUID().toString(true) +"-";
    private static final String KEY_PREFIX="lock:";
    @Override
    public boolean tryLock(long timeoutSec) {
//        获取当前线程的id
        String id =ID_PREFIX+ Thread.currentThread().getId();
//       获取锁   只有不存在的时候才成功
        Boolean aBoolean = stringRedisTemplate.opsForValue().setIfAbsent(KEY_PREFIX+name, id, timeoutSec, TimeUnit.SECONDS);

        return Boolean.TRUE.equals(aBoolean);
    }

    @Override
    public void unlock() {
        //调用lua脚本
        stringRedisTemplate.execute(
                UNLOCK_SCRIPT,
                Collections.singletonList(KEY_PREFIX+name),
                ID_PREFIX+ Thread.currentThread().getId()
        );

    }
/*
    @Override
    public void unlock() {
        //获取线程标识
        String id =ID_PREFIX+ Thread.currentThread().getId();
//        获取锁里面的标识
        String s = stringRedisTemplate.opsForValue().get(KEY_PREFIX + name);
//判断是否一致
        if(id.equals(s)){
//            释放锁
            stringRedisTemplate.delete(KEY_PREFIX+name);
        }


 */

}
