# Spring Cloud Gateway Exploration

> README.md: inaccurate due to scope change.

> An exploration into blocking, nio and reactive implementations of Spring Cloud Gateway with a caching downstream
> service and system back pressure of running such system.

Initially wanted to explore zero-copy multicast with multi-subscriber bytebuffer gateway caching. 
Redis was chosen due to the familiarity and popularity of the cache engine.
However, during the exploration, the current state of Java reactive client for Redis is confusing 
\[for me due to unfamiliarity\].
It requires much more exploration into their internal implementation to understand how the client are implementation
and whether it support the reactive streams.
After further browsing of the clients, both lettuce and reddison exposed Mono instead of Flux.
My hypothesis was to implement a gateway cache that reactively stream the byte buffer directly up and down without 
retaining any at memory. This greatly reduces the backpressure on the system as there will be no wait and everything is 
passed along reactively.

## Gateways & Service

* [Service](./service) for the gateway downstream.
* [Gateway Forward](./gateway-forward) forward request to downstream without caching. 
* [Gateway Jedis](gateway-jedis) cache with jedis client.
* [Gateway Reddison](./gateway-reddison) cache with reddison reactive client.
* [Gateway Lettuce](./gateway-lettuce) cache with lettuce reactive client.

## Goals

- [x] Service: Flexible downstream service for testing
- [x] Gateway Forward without caching for control testing
- [x] Gateway Jedis
- [x] Gateway Reddison
- [x] Gateway Lettuce
- [ ] Update README.md to reflect findings and how it's done
- [ ] Run test on AWS?
- [ ] Write an article if there is interest or potential for a topic.


## How to run?

```bash
# Build docker images
$ ./gradlew bootBuildImage

# Run gateway-forward (forward/jedis/reddison/lettuce) 
$ docker-compose up gateway-forward

# Run stress test on 'NIO' with 1k vcu
$ docker run --network host -i -v "$PWD:/benchmark" loadimpact/k6 run -e TYPE=forward /benchmark/test.js 
``` 
