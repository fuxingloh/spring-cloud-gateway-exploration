package dev.fuxing.spring;

import org.reactivestreams.Publisher;
import org.redisson.api.RBinaryStream;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.filter.NettyWriteResponseFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.setAlreadyRouted;


@Component
public class BlockingCacheFilter implements GlobalFilter, Ordered {

    private final RedissonClient redissonClient;
    private final DefaultDataBufferFactory dataBufferFactory = new DefaultDataBufferFactory();

    @Autowired
    public BlockingCacheFilter(RedissonClient redissonClient) {
        this.redissonClient = redissonClient;
    }

    @Override
    public int getOrder() {
        return NettyWriteResponseFilter.WRITE_RESPONSE_FILTER_ORDER - 1;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String key = exchange.getAttribute(CacheKeyFilter.CACHE_KEY_ATTR);

        RBinaryStream stream = redissonClient.getBinaryStream(key);
        byte[] bytes = stream.get();

        // Already exist, write response
        if (bytes != null) {
            setAlreadyRouted(exchange);
            return chain.filter(exchange).then(Mono.defer(() -> {
                ServerHttpResponse response = exchange.getResponse();
                response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
                response.getHeaders().setContentLength(bytes.length);
                return response.writeWith(Flux.just(dataBufferFactory.wrap(bytes)));
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
                    stream.set(bytes);
                }));
            }
        };
        return chain.filter(exchange.mutate().response(modified).build());
    }
}
