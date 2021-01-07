package dev.fuxing.spring;

import org.redisson.api.RBinaryStreamReactive;
import org.redisson.api.RedissonReactiveClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.config.GatewayProperties;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.NettyWriteResponseFilter;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.server.RequestPath;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.Connection;

import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.CLIENT_RESPONSE_CONN_ATTR;

@Component
public class CacheWriteFilter extends NettyWriteResponseFilter {

    private final RedissonReactiveClient redisson;

    @Autowired
    public CacheWriteFilter(GatewayProperties properties, RedissonReactiveClient redisson) {
        super(properties.getStreamingMediaTypes());
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
        return chain.filter(exchange)
                .doOnError(throwable -> dispose(exchange))
                .then(Mono.defer(() -> {
                    Connection connection = exchange.getAttribute(CLIENT_RESPONSE_CONN_ATTR);
                    if (connection == null) {
                        return Mono.empty();
                    }

                    RequestPath path = exchange.getRequest().getPath();
                    ServerHttpResponse response = exchange.getResponse();

                    Flux<DataBuffer> body = connection
                            .inbound()
                            .receive()
                            .retain()
                            .map(byteBuf -> wrap(byteBuf, response))
                            .publish()
                            .autoConnect(2);

                    return Mono.zip(
                            response.writeWith(body),
                            DataBufferUtils.join(body)
                                    .map(DataBuffer::asByteBuffer)
                                    .flatMap(buffer -> {
                                        RBinaryStreamReactive stream = redisson.getBinaryStream(path.value());
                                        return stream.write(buffer);
                                    })
                    ).then();
                })).doOnCancel(() -> dispose(exchange));
    }

    private void dispose(ServerWebExchange exchange) {
        Connection connection = exchange.getAttribute(CLIENT_RESPONSE_CONN_ATTR);
        if (connection != null) {
            connection.dispose();
        }
    }
}
