# Spring Cloud Gateway Exploration

[![Load Testing](https://github.com/fuxingloh/spring-cloud-gateway-exploration/workflows/Load%20Testing/badge.svg)](https://github.com/fuxingloh/spring-cloud-gateway-exploration/actions?query=workflow%3A%22Load+Testing%22)

> An exploration into blocking, nio and reactive implementations of Spring Cloud Gateway with a caching downstream
> service and system back pressure of running such system.

Initially, I wanted to explore zero-copy multicast with multi-subscriber bytebuffer gateway caching. Redis was chosen due
to the familiarity and popularity of the cache engine. However, during the exploration, the current state of Java
reactive client for Redis is confusing for me due to unfamiliarity.

It requires much more exploration into their internal implementation to understand how the client are implemented and
whether it support the reactive streams. After further browsing of the clients, both lettuce and reddison exposed Mono
instead of Flux.

My hypothesis was to implement a gateway cache that reactively stream the byte buffer directly up and down without
retaining any at memory. This greatly reduces the backpressure on the system as there will be no wait and everything is
passed along reactively. There was motivation to create such a client for this demo, but the complexity was a deal
breaker for me.

At the end of the day, reactivity is hard, you don't choose reactivity for just performance.
There are also much beauty of how things are done in the Spring Framework, Reactor and WebFlux.

FYI, all 4 gateway implementation sucks, don't take them as reference.

## Gateways & Service

At the end, I used the 3 most popular redis client and ran the test [locally](.github/workflows/load.yml). You can
explore the sub modules for more information of the implementation.

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
- [ ] Run test on AWS?
- [ ] Write an article if there is interest or potential for a topic.

## How to run?

```bash
# Build docker images
$ ./gradlew bootBuildImage

# Run gateway-forward (forward/jedis/reddison/lettuce) 
$ docker-compose up gateway-forward

# Run stress test on 'forward' with 1k vcu
$ docker run --network host -i -v "$PWD:/benchmark" loadimpact/k6 run -e TYPE=forward /benchmark/test.js 
```
