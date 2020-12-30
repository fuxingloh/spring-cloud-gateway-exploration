package dev.fuxing.spring;

import org.redisson.api.RBinaryStreamReactive;
import org.redisson.api.RedissonReactiveClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.filter.NettyWriteResponseFilter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.server.RequestPath;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Objects;

import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.setAlreadyRouted;

@Component
public class ReactiveCacheReadFilter implements GlobalFilter, Ordered {

    private final RedissonReactiveClient redissonClient;

    @Autowired
    public ReactiveCacheReadFilter(RedissonReactiveClient redissonClient) {
        this.redissonClient = redissonClient;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        Route route = exchange.getAttribute(GATEWAY_ROUTE_ATTR);
        Objects.requireNonNull(route);

        // Specific to this example just to check if route is reactive.
        if (!"reactive".equals(route.getId())) {
            return chain.filter(exchange);
        }

        RequestPath path = exchange.getRequest().getPath();
        RBinaryStreamReactive stream = redissonClient.getBinaryStream(path.value());

        return stream.get()
                .doOnError(throwable -> chain.filter(exchange))
                .filter(Objects::nonNull)
                .flatMap(bytes -> {
                    setAlreadyRouted(exchange);
                    return chain.filter(exchange).then(Mono.defer(() -> {
                        ServerHttpResponse response = exchange.getResponse();
                        DataBuffer dataBuffer = response.bufferFactory().allocateBuffer();
                        dataBuffer.write(bytes);
                        return response.writeWith(Flux.just(dataBuffer));
                    }));
                })
                .switchIfEmpty(chain.filter(exchange));
    }

    @Override
    public int getOrder() {
        return NettyWriteResponseFilter.WRITE_RESPONSE_FILTER_ORDER - 1;
    }
}
