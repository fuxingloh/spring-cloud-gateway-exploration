package dev.fuxing.spring;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class ForwardApplication {

    public static void main(final String[] args) {
        SpringApplication.run(ForwardApplication.class, args);
    }

    @Value("${SVC_URI:http://localhost:10100}")
    private String svcUri;

    @Bean
    public RouteLocator customRouteLocator(RouteLocatorBuilder builder) {
        return builder.routes()
                .route(r -> r.path("/api/**")
                        .filters(f -> f.rewritePath("/api/(?<path>.*)", "/${path}"))
                        .uri(svcUri))
                .build();
    }

}
