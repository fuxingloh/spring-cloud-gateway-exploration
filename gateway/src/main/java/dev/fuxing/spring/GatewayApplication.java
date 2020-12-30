package dev.fuxing.spring;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class GatewayApplication {

    public static void main(final String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }

    @Value("${SVC_EXPENSIVE_URI:http://localhost:10100}")
    private String expensiveUri;

    @Bean
    public RouteLocator customRouteLocator(RouteLocatorBuilder builder) {
        return builder.routes()
                .route("forward", r -> r.path("/api/forward/*")
                        .filters(f -> f.rewritePath("/api/forward/(?<id>.*)", "/expensive/${id}"))
                        .uri(expensiveUri))
                .route("blocking", r -> r.path("/api/blocking/*")
                        .filters(f -> f.rewritePath("/api/blocking/(?<id>.*)", "/expensive/${id}"))
                        .uri(expensiveUri))
                .route("reactive", r -> r.path("/api/reactive/*")
                        .filters(f -> f.rewritePath("/api/reactive/(?<id>.*)", "/expensive/${id}"))
                        .uri(expensiveUri))
                .build();
    }

}
