package com.hmdp.utils;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.CountDownLatch;

/**
 * @author 温柔的烟火
 * @date 2024/10/31-13:40
 */
@Component
public class RedisIdWorker {
    /**
     * 开始的时间戳
     */
    private static final long BEGIN_TIMESTAMP=1730073600L;
    private static final int COUNT_BITS=32;
    private StringRedisTemplate stringRedisTemplate;

    public RedisIdWorker(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public long nextId(String keyPrefix){
//        生成时间戳
        LocalDateTime now = LocalDateTime.now();
        long nowSecond = now.toEpochSecond(ZoneOffset.UTC);
        long timestamp=nowSecond- BEGIN_TIMESTAMP;
//        生成序列号
//        获取当前日期，精确到天
        String date = now.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        Long count = stringRedisTemplate.opsForValue().increment("icr:" + keyPrefix +":"+ date);
//        生成序列号
//        拼接并返回
        return timestamp<<COUNT_BITS|count;
    }

    public static void main(String[] args) {
        LocalDateTime time=LocalDateTime.of(2024,10,28,0,0,0);
        long second=time.toEpochSecond(ZoneOffset.UTC);
        System.out.println("second = " + second);
    }
}
