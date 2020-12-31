# Spring Cloud Gateway NIO Demo

> An amateur exploration into reactive NIO API Gateway with cache design.

Initially wanted to explore zero-copy multicast (multi subscriber) bytebuffer gateway caching,
but the current state of reactive client requires much more codes and exploration into the source to get it to work
reactively.

The redis libraries (lettuce and reddison) that I have explored and dug into the source code both exposed Mono instead 
of Flux.
The idea was to stream the byte buffer directly from up to down and down to up without retaining any at memory.
This will greatly reduce any backpressure on the system as there is no wait and everything is passed along reactively.
Although spring gateway design allow non-blocking, the oddities of cache client is another story. 

The example here is non-blocking but not truly reactive in that sense.

#### Blocking Cache Filter

This is just hastily done as a control test. Aka the wrong way.

#### Reactive Cache Read Filter

0. Run before NettyRoutingFilter
1. Get BinaryStream from redis
2. If exist; `setAlreadyRouted(exchange)`
   0. flatMap `byte[]` to `Mono<Void>` and chain response
   1. allocateBuffer()
   2. writeBuffer into response
   * However, it was `Mono<byte[]>` which mean it has to retain and read all into memory
   * I was expecting `Flux<SomeBuffer>`, and when the first signal indicate it's cached to publish upstream immediately.
3. If empty; `chain.filter(exchange)`

#### Reactive Cache Write Filter 

0. Run before NettyWriteResponseFilter to intercept the original netty filter
1. Get Netty Connection .inbound().receive().retain()
   * Not sure if it's done properly, but didn't explore as the main hypothesis cannot be tested.
2. Flux publish, autoConnect(2) to share the connection with 2 subscriber
   * This causes the pull to only start when 2 subscribed
3. First subscriber is just netty upstream, response.writeWith and return `Mono<Void>`
4. Second subscriber is redis client
   1. `DataBufferUtils.join(body)`
   2. map to ByteBuffer
   3. write stream
   4. subscribe to start the auto connect
   * As we have to write 1 ByteBuffer instead of publish multi ByteBuffer. It has to be converted to Mono.
   * And that is done through `DataBufferUtils.join(body)`.   
   * Which might cause back pressure in system as it has to be retained.

### Conclusion

It's asynchronous non-blocking-ish at best. But not entirely reactive from what I envisioned.
One could look harder for a client that allow the behavior described or write the client themselves.
As this was just a one-day exploration for me, I will stop right here. 

## How to run?

```shell
./gradlew bootBuildImage
docker-compose up
``` 
