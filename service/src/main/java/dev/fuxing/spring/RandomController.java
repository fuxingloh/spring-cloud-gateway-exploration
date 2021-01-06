package dev.fuxing.spring;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@RestController
public class RandomController {

    @GetMapping("/length/{length}/delay/{delay}")
    private Mono<List<DataStructure>> get(@PathVariable int length, @PathVariable int delay) {
        return Mono.delay(Duration.ofMillis(delay))
                .map(l -> IntStream.range(0, length)
                        .mapToObj(operand -> {
                            DataStructure structure = new DataStructure();
                            structure.setId(UUID.randomUUID().toString());
                            return structure;
                        })
                        .collect(Collectors.toList()));
    }

    public static class DataStructure {
        private String id;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }
    }
}
