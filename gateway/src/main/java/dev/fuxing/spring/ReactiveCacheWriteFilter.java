package dev.fuxing.spring;

import io.netty.buffer.ByteBuf;
import org.redisson.RedissonReactive;
import org.redisson.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.config.GatewayProperties;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.filter.NettyWriteResponseFilter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.MediaType;
import org.springframework.http.server.RequestPath;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.ByteBufFlux;
import reactor.netty.ByteBufMono;
import reactor.netty.Connection;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Objects;

import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.*;

@Component
public class ReactiveCacheWriteFilter extends NettyWriteResponseFilter {

    private final RedissonReactiveClient redissonClient;

    @Autowired
    public ReactiveCacheWriteFilter(GatewayProperties properties, RedissonReactiveClient redissonClient) {
        super(properties.getStreamingMediaTypes());
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

        return chain.filter(exchange)
                .doOnError(throwable -> dispose(exchange))
                .then(Mono.defer(() -> {
                    Connection connection = exchange.getAttribute(CLIENT_RESPONSE_CONN_ATTR);
                    if (connection == null) {
                        return Mono.empty();
                    }

                    ServerHttpResponse response = exchange.getResponse();

                    Flux<DataBuffer> body = connection
                            .inbound()
                            .receive()
                            .retain()
                            .map(byteBuf -> wrap(byteBuf, response))
                            .publish()
                            .autoConnect(2);

                    // min subscribers: 2 won't run until netty channel connects
                    // noinspection CallingSubscribeInNonBlockingScope
                    Disposable disposable = DataBufferUtils.join(body)
                            .map(DataBuffer::asByteBuffer)
                            .flatMap(byteBuffer -> {
                                RequestPath path = exchange.getRequest().getPath();
                                RBinaryStreamReactive stream = redissonClient.getBinaryStream(path.value());
                                return stream.write(byteBuffer);
                            }).subscribe();

                    return response.writeWith(body)
                            .doOnError(e -> disposable.dispose())
                            .doOnCancel(disposable::dispose);
                })).doOnCancel(() -> dispose(exchange));
    }

    private void dispose(ServerWebExchange exchange) {
        Connection connection = exchange.getAttribute(CLIENT_RESPONSE_CONN_ATTR);
        if (connection != null) {
            connection.dispose();
        }
    }

    /**
     * @return order so that it rules before 'WRITE_RESPONSE_FILTER_ORDER'
     */
    @Override
    public int getOrder() {
        return NettyWriteResponseFilter.WRITE_RESPONSE_FILTER_ORDER + 1;
    }
}
