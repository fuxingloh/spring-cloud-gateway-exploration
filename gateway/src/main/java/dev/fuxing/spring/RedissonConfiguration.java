package dev.fuxing.spring;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.api.RedissonReactiveClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RedissonConfiguration {

    @Value("${REDIS_ADDRESS:redis://localhost:16379}")
    private String redisAddress;

    @Bean
    Config config() {
        Config config = new Config();
        config.useSingleServer()
                .setAddress(redisAddress);
        return config;
    }

    @Bean
    RedissonClient redissonClient(Config config) {
        return Redisson.create(config);
    }

    @Bean
    RedissonReactiveClient redissonReactiveClient(Config config) {
        return Redisson.createReactive(config);
    }
}
