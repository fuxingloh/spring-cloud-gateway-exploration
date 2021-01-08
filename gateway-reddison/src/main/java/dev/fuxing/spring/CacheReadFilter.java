package dev.fuxing.spring;

import org.redisson.api.RBinaryStreamReactive;
import org.redisson.api.RedissonReactiveClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.setAlreadyRouted;

@Component
public class CacheReadFilter implements GlobalFilter, Ordered {

    private final RedissonReactiveClient redissonClient;

    @Autowired
    public CacheReadFilter(RedissonReactiveClient redissonClient) {
        this.redissonClient = redissonClient;
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE - 1;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String key = exchange.getAttribute(CacheKeyFilter.CACHE_KEY_ATTR);
        RBinaryStreamReactive stream = redissonClient.getBinaryStream(key);

        return stream.get()
                .map(bytes -> exchange.getResponse().bufferFactory().wrap(bytes))
                .doOnSuccess(buffer -> {
                    setAlreadyRouted(exchange);
                    exchange.getAttributes().put(CacheReadFilter.class.getName(), buffer);

                    HttpHeaders headers = exchange.getResponse().getHeaders();
                    headers.setContentType(MediaType.APPLICATION_JSON);
                    headers.setContentLength(buffer.readableByteCount());
                }).then(chain.filter(exchange));
    }
}
