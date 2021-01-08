package dev.fuxing.spring;

import org.redisson.api.RBinaryStreamReactive;
import org.redisson.api.RedissonReactiveClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.support.TimeoutException;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.core.io.buffer.NettyDataBufferFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;

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

        Mono<DataBuffer> response = stream.get()
                .map(bytes -> wrap(bytes, exchange.getResponse()))
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

    private DataBuffer wrap(byte[] bytes, ServerHttpResponse response) {
        DataBufferFactory bufferFactory = response.bufferFactory();
        if (bufferFactory instanceof NettyDataBufferFactory) {
            NettyDataBufferFactory factory = (NettyDataBufferFactory) bufferFactory;
            return factory.wrap(bytes);
        }
        // MockServerHttpResponse creates these
        else if (bufferFactory instanceof DefaultDataBufferFactory) {
            return ((DefaultDataBufferFactory) bufferFactory).wrap(bytes);
        }
        throw new IllegalArgumentException("Unknown DataBufferFactory type " + bufferFactory.getClass());
    }
}
