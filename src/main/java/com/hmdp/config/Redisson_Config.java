package com.hmdp.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author 温柔的烟火
 * @date 2024/11/5-10:15
 */
@Configuration
public class Redisson_Config {

    @Bean
    public RedissonClient redissonConfig(){
        //配置
        Config config = new Config();
        config.useSingleServer().setAddress("redis://192.168.1.144:6379").setPassword("jinyubo");
//        创建redisclient对象
        return Redisson.create(config);
    }

}
