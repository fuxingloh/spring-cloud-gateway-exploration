package dev.fuxing.spring;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import redis.clients.jedis.Jedis;

import java.net.URI;

@Configuration
public class JedisConfig {

    @Value("${REDIS_ADDRESS:redis://localhost:16379}")
    private String redisAddress;

    @Bean
    Jedis client() {
        return new Jedis(URI.create(redisAddress));
    }

}
