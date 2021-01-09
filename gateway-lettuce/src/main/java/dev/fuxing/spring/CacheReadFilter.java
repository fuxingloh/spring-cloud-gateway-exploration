package dev.fuxing.spring;

import io.lettuce.core.api.reactive.RedisStringReactiveCommands;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.ByteBuffer;
import java.util.Objects;

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
                .map(buffer -> exchange.getResponse().bufferFactory().wrap(buffer))
                .doOnNext(buffer -> {
                    setAlreadyRouted(exchange);
                    exchange.getAttributes().put(CacheReadFilter.class.getName(), buffer);

                    HttpHeaders headers = exchange.getResponse().getHeaders();
                    headers.setContentType(MediaType.APPLICATION_JSON);
                    headers.setContentLength(buffer.readableByteCount());
                }).then(chain.filter(exchange));
    }
}
