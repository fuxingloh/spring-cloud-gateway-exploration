package dev.fuxing.spring;

import org.reactivestreams.Publisher;
import org.redisson.api.RBinaryStreamReactive;
import org.redisson.api.RedissonReactiveClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.filter.NettyWriteResponseFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.*;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;


@Component
public class CacheMissFilter implements GlobalFilter, Ordered {

    private final RedissonReactiveClient redisson;

    @Autowired
    public CacheMissFilter(RedissonReactiveClient redisson) {
        this.redisson = redisson;
    }

    @Override
    public int getOrder() {
        return NettyWriteResponseFilter.WRITE_RESPONSE_FILTER_ORDER - 1;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // noinspection NullableProblems
        ServerHttpResponse modified = new ServerHttpResponseDecorator(exchange.getResponse()) {
            /**
             * @param body intercepted from NettyWriteResponseFilter
             */
            @Override
            public Mono<Void> writeWith(Publisher<? extends DataBuffer> body) {
                if (exchange.getAttributes().containsKey(CacheReadFilter.class.getName())) {
                    return super.writeWith(body);
                }

                String key = exchange.getAttribute(CacheKeyFilter.CACHE_KEY_ATTR);
                RBinaryStreamReactive stream = redisson.getBinaryStream(key);

                Flux<? extends DataBuffer> flux = Flux.from(body)
                        .publish()
                        .autoConnect(2);

                return super.writeWith(flux).and(
                        DataBufferUtils.join(flux)
                                .flatMap(buf -> stream.write(buf.asByteBuffer()))
                );
            }

            @Override
            public Mono<Void> writeAndFlushWith(Publisher<? extends Publisher<? extends DataBuffer>> body) {
                return writeWith(Flux.from(body).flatMapSequential(p -> p));
            }
        };

        return chain.filter(exchange.mutate().response(modified).build());
    }
}
