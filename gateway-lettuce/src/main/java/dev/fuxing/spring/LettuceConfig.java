package dev.fuxing.spring;

import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.reactive.RedisStringReactiveCommands;
import io.lettuce.core.codec.RedisCodec;
import io.lettuce.core.codec.StringCodec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.ByteBuffer;

@Configuration
public class LettuceConfig {

    @Value("${REDIS_ADDRESS:redis://localhost:16379}")
    private String redisAddress;

    @Bean
    RedisClient config() {
        return RedisClient.create(redisAddress);
    }

    @Bean
    RedisStringReactiveCommands<String, ByteBuffer> client(RedisClient client) {
        StatefulRedisConnection<String, ByteBuffer> connection = client.connect(new StringByteBufferRedisCodec());
        return connection.reactive();
    }

    private static class StringByteBufferRedisCodec implements RedisCodec<String, ByteBuffer> {

        private static final StringCodec stringCodec = StringCodec.UTF8;

        @Override
        public String decodeKey(ByteBuffer bytes) {
            return stringCodec.decodeKey(bytes);
        }

        @Override
        public ByteBuffer decodeValue(ByteBuffer bytes) {
            return bytes;
        }

        @Override
        public ByteBuffer encodeKey(String key) {
            return stringCodec.encodeKey(key);
        }

        @Override
        public ByteBuffer encodeValue(ByteBuffer value) {
            return value;
        }
    }
}
