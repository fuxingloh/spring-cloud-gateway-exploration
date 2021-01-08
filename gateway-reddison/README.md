# Gateway Reddison

> Reddison & Lettuce gateway implementation in this example are written to make use of the pre-existing client
> provided features.
> They are written as "best effort" intention. Your mileage may vary.

## Cache Key Filter

To generate a consistent cache key to be used for read and write as path can be altered when passed through the filter
chain.

## Cache Read Filter

1. Run before NettyRoutingFilter
2. Get BinaryStream from redis
3. If cached; `setAlreadyRouted(exchange)` and write response
4. If empty; `chain.filter(exchange)` and continue to NettyRoutingFilter

It was `Mono<byte[]>` which mean it has to retain and read all into memory I was expecting `Flux<SomeBuffer>`, and when
the first signal indicate it's cached to publish upstream immediately.

## Cache Write Filter

1. Run before NettyWriteResponseFilter
2. Modified HttpResponse to intercept `writeWith`
3. If already cached, pass through and ignore
4. Else, `Flux.publish().autoConnect(2)` to share the connection with 2 subscriber
5. `Mono.zip` to wrap both connection, so they get pulled together.
6. First connection write to `super.writeWith`
7. Second connection write to redis client.

## Hypothesis and Conclusion

> Correct me if I am wrong.

The example here is non-blocking but not truly flux reactive in that sense.

The redis libraries (lettuce and reddison) that I have explored and dug into the source code both exposed Mono instead
of Flux. The idea was to stream the byte buffer directly from up to down and down to up without retaining any at memory.
This will greatly reduce any backpressure on the system as there is no wait and everything is passed along reactively.
Although spring gateway design allow non-blocking, the oddities of cache client is another story.
