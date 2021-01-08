package dev.fuxing.spring;

import org.redisson.api.RBinaryStreamReactive;
import org.redisson.api.RedissonReactiveClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
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
                .onErrorResume(throwable -> Mono.empty())
                .switchIfEmpty(chain.filter(exchange).then(Mono.empty()))
                .flatMap(bytes -> {
                    setAlreadyRouted(exchange);
                    exchange.getAttributes().put(CacheReadFilter.class.getName(), true);

                    return chain.filter(exchange).then(Mono.defer(() -> {
                        ServerHttpResponse response = exchange.getResponse();
                        DataBuffer dataBuffer = response.bufferFactory().wrap(bytes);

                        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
                        response.getHeaders().setContentLength(bytes.length);
                        return response.writeWith(Flux.just(dataBuffer));
                    }));
                });
    }
}
