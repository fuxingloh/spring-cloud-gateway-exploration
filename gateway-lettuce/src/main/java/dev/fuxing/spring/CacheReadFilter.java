package dev.fuxing.spring;

import io.lettuce.core.api.reactive.RedisStringReactiveCommands;
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

import java.nio.ByteBuffer;

import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.setAlreadyRouted;

@Component
public class CacheReadFilter implements GlobalFilter, Ordered {

    private final RedisStringReactiveCommands<String, ByteBuffer> client;

    @Autowired
    public CacheReadFilter(RedisStringReactiveCommands<String, ByteBuffer> client) {
        this.client = client;
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE - 1;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String key = exchange.getAttribute(CacheKeyFilter.CACHE_KEY_ATTR);

        return client.get(key)
                .onErrorResume(throwable -> Mono.empty())
                .switchIfEmpty(chain.filter(exchange).then(Mono.empty()))
                .flatMap(buffer -> {
                    setAlreadyRouted(exchange);
                    exchange.getAttributes().put(CacheReadFilter.class.getName(), true);

                    return chain.filter(exchange).then(Mono.defer(() -> {
                        ServerHttpResponse response = exchange.getResponse();
                        DataBuffer dataBuffer = response.bufferFactory().wrap(buffer);

                        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
                        response.getHeaders().setContentLength(dataBuffer.readableByteCount());
                        return response.writeWith(Flux.just(dataBuffer));
                    }));
                });
    }
}
