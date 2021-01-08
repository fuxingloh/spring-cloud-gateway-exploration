# Gateway Jedis

Not specifically blocking but an awkward way to do it in Spring Cloud Gateway. 

## Blocking Cache Filter

0. Path rewrite, applied to all gateway.
1. Runs before `WRITE_RESPONSE_FILTER_ORDER`
2. Use path of api request to get the binary stream from redisson client.
3. If bytes exists:
   1. `setAlreadyRouted`
   2.  Write to response with `writeWith(Flux.just())`
4. Else, if bytes don't exist:
   1. `WRITE_RESPONSE_FILTER_ORDER` is before; so it will run after response is received.
   2. Mutate exchange to intercept response from `NettyWriteResponseFilter`
   3. `DataBufferUtils.join(body)` to wait for all bytes.
   4. `doOnSuccess` to save the response to cache library.
