package dev.fuxing.spring;

import org.reactivestreams.Publisher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.filter.NettyWriteResponseFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import redis.clients.jedis.Jedis;

import java.nio.charset.StandardCharsets;

import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.setAlreadyRouted;


@Component
public class CacheFilter implements GlobalFilter, Ordered {

    private final Jedis jedis;

    @Autowired
    public CacheFilter(Jedis jedis) {
        this.jedis = jedis;
    }

    @Override
    public int getOrder() {
        return NettyWriteResponseFilter.WRITE_RESPONSE_FILTER_ORDER - 1;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String key = exchange.getAttribute(CacheKeyFilter.CACHE_KEY_ATTR);
        assert key != null;
        byte[] keyBytes = key.getBytes(StandardCharsets.UTF_8);
        byte[] bytes = jedis.get(keyBytes);

        // Already exist, write response
        if (bytes != null) {
            setAlreadyRouted(exchange);
            return chain.filter(exchange).then(Mono.defer(() -> {
                ServerHttpResponse response = exchange.getResponse();
                response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
                response.getHeaders().setContentLength(bytes.length);

                DataBuffer buffer = response.bufferFactory().wrap(bytes);
                return response.writeWith(Flux.just(buffer));
            }));
        }

        // Not cached yet, defer and cache once received
        // noinspection NullableProblems
        ServerHttpResponse modified = new ServerHttpResponseDecorator(exchange.getResponse()) {
            @Override
            public Mono<Void> writeWith(Publisher<? extends DataBuffer> body) {
                Mono<DataBuffer> joined = DataBufferUtils.join(body);
                return super.writeWith(joined.doOnSuccess(dataBuffer -> {
                    byte[] bytes = new byte[dataBuffer.readableByteCount()];
                    dataBuffer.asByteBuffer().get(bytes);
                    jedis.set(keyBytes, bytes);
                }));
            }
        };
        return chain.filter(exchange.mutate().response(modified).build());
    }
}
