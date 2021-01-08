package dev.fuxing.spring;

import org.reactivestreams.Publisher;
import org.redisson.api.RBinaryStreamReactive;
import org.redisson.api.RedissonReactiveClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.filter.NettyWriteResponseFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.server.RequestPath;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.util.annotation.NonNull;

import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.isAlreadyRouted;


@Component
public class CacheWriteFilter implements GlobalFilter, Ordered {

    private final RedissonReactiveClient redisson;

    @Autowired
    public CacheWriteFilter(RedissonReactiveClient redisson) {
        this.redisson = redisson;
    }

    /**
     * @return order so that it rules before 'WRITE_RESPONSE_FILTER_ORDER'
     */
    @Override
    public int getOrder() {
        return NettyWriteResponseFilter.WRITE_RESPONSE_FILTER_ORDER + 1;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (isAlreadyRouted(exchange)) {
            return chain.filter(exchange);
        }

        RequestPath path = exchange.getRequest().getPath();
        RBinaryStreamReactive stream = redisson.getBinaryStream(path.value());
        CachingHttpResponse response = new CachingHttpResponse(exchange, stream);
        return chain.filter(exchange.mutate().response(response).build());
    }

    static class CachingHttpResponse extends ServerHttpResponseDecorator {

        private final RBinaryStreamReactive stream;

        public CachingHttpResponse(ServerWebExchange exchange, RBinaryStreamReactive stream) {
            super(exchange.getResponse());
            this.stream = stream;
        }

        @NonNull
        @Override
        public Mono<Void> writeWith(@NonNull Publisher<? extends DataBuffer> body) {
            return Mono.zip(
                    super.writeWith(body),
                    DataBufferUtils.join(body)
                            .map(DataBuffer::asByteBuffer)
                            .flatMap(stream::write)
            ).then();
        }
    }
}
