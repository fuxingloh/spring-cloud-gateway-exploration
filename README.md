# Spring Cloud Gateway Exploration

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
* [Gateway Blocking](./gateway-blocking) cache with blocking, aka the wrong way.
* [Gateway Reddison](./gateway-reddison) cache with reddison reactive client.
* [Gateway Lettuce](./gateway-lettuce) cache with lettuce reactive client.
* [Gateway Reactive](./gateway-reactive) cache with custom reactive client using flux.

## Goals

- [ ] Reactive Redis Client with Netty
- [ ] Run test on AWS ECS?


## How to run?

```bash
# Build docker images
$ ./gradlew bootBuildImage

# Run gateway-blocking (forward/blocking/reddison/lettuce/reactive) 
$ docker-compose up gateway-blocking

# Run stress test on 'NIO' with 1k vcu
$ docker run --network host -i -v "$PWD:/benchmark" loadimpact/k6 run -e TYPE=blocking /benchmark/test.js 
``` 
