package dev.fuxing.spring;

import org.reactivestreams.Publisher;
import org.redisson.Redisson;
import org.redisson.api.RBinaryStream;
import org.redisson.api.RMap;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.filter.NettyWriteResponseFilter;
import org.springframework.cloud.gateway.filter.factory.rewrite.ModifyResponseBodyGatewayFilterFactory;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DefaultDataBuffer;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.server.RequestPath;
import org.springframework.http.server.reactive.HttpHeadResponseDecorator;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import javax.validation.constraints.NotNull;
import java.net.URI;
import java.nio.charset.Charset;
import java.util.Objects;

import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.*;


@Component
public class BlockingCacheFilter implements GlobalFilter, Ordered {

    private final RedissonClient redissonClient;
    private final DefaultDataBufferFactory dataBufferFactory = new DefaultDataBufferFactory();

    @Autowired
    public BlockingCacheFilter(RedissonClient redissonClient) {
        this.redissonClient = redissonClient;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        Route route = exchange.getAttribute(GATEWAY_ROUTE_ATTR);
        Objects.requireNonNull(route);

        if (!"blocking".equals(route.getId())) {
            return chain.filter(exchange);
        }

        RequestPath path = exchange.getRequest().getPath();
        RBinaryStream stream = redissonClient.getBinaryStream(path.value());
        byte[] bytes = stream.get();

        // Not cached yet, defer and cache once received
        if (bytes == null) {
            ServerHttpResponse response = new ServerHttpResponseDecorator(exchange.getResponse()) {
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

            return chain.filter(exchange.mutate().response(response).build());
        }

        // Already exist, write response
        setAlreadyRouted(exchange);
        return chain.filter(exchange).then(Mono.defer(() -> {
            ServerHttpResponse response = exchange.getResponse();
            return response.writeWith(Flux.just(dataBufferFactory.wrap(bytes)));
        }));
    }

    @Override
    public int getOrder() {
        return NettyWriteResponseFilter.WRITE_RESPONSE_FILTER_ORDER - 1;
    }
}
