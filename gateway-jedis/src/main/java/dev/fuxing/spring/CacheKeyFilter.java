package dev.fuxing.spring;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.RequestPath;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Due to path rewrite filter and etc path is not guarantee to be consistent throughout the filter chain.
 * Best is to set it to a exchange attributes.
 */
@Component
public class CacheKeyFilter implements GlobalFilter, Ordered {

    static final String CACHE_KEY_ATTR = CacheKeyFilter.class.getName();

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        RequestPath path = exchange.getRequest().getPath();
        exchange.getAttributes().put(CACHE_KEY_ATTR, path.value());
        return chain.filter(exchange);
    }
}
