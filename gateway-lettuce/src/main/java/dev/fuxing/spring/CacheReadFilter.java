package dev.fuxing.spring;

import io.lettuce.core.api.reactive.RedisStringReactiveCommands;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.support.TimeoutException;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.ByteBuffer;
import java.time.Duration;

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

        Mono<DataBuffer> response = client.get(key)
                .map(buffer -> exchange.getResponse().bufferFactory().wrap(buffer))
                .timeout(Duration.ofSeconds(30), Mono.error(new TimeoutException()))
                .onErrorMap(TimeoutException.class, th -> new ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT, th.getMessage(), th));

        return response
                .flatMap(dataBuffer -> {
                    setAlreadyRouted(exchange);
                    exchange.getAttributes().put(CacheReadFilter.class.getName(), true);

                    HttpHeaders headers = exchange.getResponse().getHeaders();
                    headers.setContentType(MediaType.APPLICATION_JSON);
                    headers.setContentLength(dataBuffer.readableByteCount());

                    return exchange.getResponse().writeWith(Flux.just(dataBuffer));
                })
                .then(chain.filter(exchange));
    }
}
