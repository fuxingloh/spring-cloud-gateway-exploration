package dev.fuxing.spring;

import org.apache.commons.lang3.RandomStringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.time.Duration;

@RestController
@RequestMapping("/expensive")
public class ExpensiveController {

    @GetMapping("/{id}")
    private Mono<String> getEmployeeById(@PathVariable int id) {
        return Mono.delay(Duration.ofSeconds(2))
                .map(aLong -> RandomStringUtils.randomAlphanumeric(id));
    }

}
