package dev.fuxing.spring;

import org.apache.commons.lang3.RandomStringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.time.Duration;

@RestController
public class RandomController {

    @GetMapping("/length/{length}/delay/{delay}")
    private Mono<String> get(@PathVariable int length, @PathVariable int delay) {
        return Mono.delay(Duration.ofMillis(delay))
                .map(l -> RandomStringUtils.randomAlphanumeric(length));
    }
}
