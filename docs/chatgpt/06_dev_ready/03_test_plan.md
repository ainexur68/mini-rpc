# MiniRPC 1.0 Test Plan (Unit / Integration / Performance)

> This is a concrete checklist: test class names, what to assert, and minimal setup.

---

## 1. Test Categories

- **Unit Tests (UT)**: no external dependency (no Redis).
- **Integration Tests (IT)**: may require external dependency (Redis). Prefer Testcontainers if allowed.
- **Performance/PoC**: optional microbench / load test harness (not gated for CI).

---

## 2. Protocol Tests (`minirpc-protocol`)

### UT-P1 Half packet decode
**Class**: `com.minirpc.protocol.codec.MiniRpcFrameDecoderHalfPacketTest`

**Given**
- One encoded frame bytes
- Feed into decoder in 3 chunks

**Assert**
- Exactly 1 `MiniRpcFrame` output
- fields match (magic/version/requestId/bodyLength)

### UT-P2 Sticky packets decode
**Class**: `MiniRpcFrameDecoderStickyPacketTest`

**Given**
- bytes(frame1) + bytes(frame2) concatenated

**Assert**
- output frames == 2 in correct order

### UT-P3 Invalid magic
**Class**: `MiniRpcFrameDecoderInvalidMagicTest`

**Assert**
- decoder throws or channel closed (choose deterministic behavior and document)

---

## 3. Serialization Tests (`minirpc-serialization`)
> 1.0 ships JSON only; Kryo is out of scope for 1.0 tests.

### UT-S1 JSON round-trip request
**Class**: `com.minirpc.serialization.json.JsonSerializerTest`

**Assert**
- request serialized then deserialized equals expected fields
- attachments preserved

### UT-S2 SPI discovery
**Class**: `com.minirpc.serialization.SerializerRegistryTest`

**Assert**
- `new SerializerRegistry().required((byte)0)` returns JsonSerializer

---

## 4. Transport Tests (`minirpc-transport-netty`)

### IT-T1 Server/client basic
**Class**: `com.minirpc.transport.netty.NettyTransportBasicIT`

**Setup**
- start server on random port with `RequestHandler` echo implementation
- client send request

**Assert**
- future completes
- response.requestId matches requestId
- response.code == OK

### IT-T2 Concurrency inflight
**Class**: `NettyTransportConcurrencyIT`

**Given**
- 1000 requests concurrent
- server handler sleeps 1ms (virtual thread)

**Assert**
- all futures complete
- no inflight leak (`inflightMap` size returns to 0)

### IT-T3 No business on event loop
**Class**: `NettyTransportOffloadIT`

**Assert**
- within handler, record `Thread.currentThread()` name/id
- ensure it is NOT Netty event loop thread (define heuristic: name contains "nioEventLoop" etc.)

---

## 5. Core Tests (`minirpc-core`)

### IT-C1 End-to-end hello
**Class**: `com.minirpc.core.e2e.HelloServiceE2EIT`

**Setup**
- Provider exporter registers HelloServiceImpl
- Server uses ProviderDispatcher as RequestHandler
- Consumer uses ReferenceFactory

**Assert**
- `hello("x")` returns expected string
- traceId exists in attachments (governance later can formalize)

### UT-C2 Method overload resolution
**Class**: `ProviderDispatcherOverloadTest`

**Assert**
- when paramTypeNames differ, correct method selected

---

## 6. Governance Tests (`minirpc-governance`)

### UT-G1 Timeout
**Class**: `TimeoutFilterTest`

**Given**
- handler sleeps 200ms
- timeout is 50ms

**Assert**
- throws RpcException TIMEOUT

### UT-G2 Retry once
**Class**: `RetryFilterTest`

**Given**
- first call fails with CONNECTION_CLOSED
- second succeeds

**Assert**
- total invokes == 2
- result success

### UT-G3 TraceId
**Class**: `TraceFilterTest`

**Assert**
- if request has no traceId, filter injects one

---

## 7. Registry Tests (`minirpc-registry-redis`)

### IT-R0 Redis availability strategy
Option A (recommended): **Testcontainers**  
Option B: local Redis at `localhost:6379` with `-Dredis.host/-Dredis.port`

### IT-R1 register + lookup
**Class**: `RedisRegistryRegisterLookupIT`

**Assert**
- lookup returns instance after register

### IT-R2 TTL expire
**Class**: `RedisRegistryTtlExpireIT`

**Given**
- register with ttl=2s
- wait 3s

**Assert**
- lookup returns empty

### IT-R3 Pub/Sub change notification
**Class**: `RedisRegistrySubscribeIT`

**Assert**
- after new register, subscriber callback invoked with updated list

---

## 8. LoadBalancer Tests (`minirpc-loadbalancer`)

### UT-L1 Random select
**Class**: `RandomLoadBalancerTest`
**Assert**: selected in list

### UT-L2 RoundRobin
**Class**: `RoundRobinLoadBalancerTest`
**Assert**: cycles 0..n-1 repeatedly per ServiceKey

---

## 9. Performance Harness (optional, not CI-gated)

- reuse PoC parameters:
  - server: `maxInFlight`, `bizMs`
  - client: total requests, threads, connections
- output:
  - total cost, QPS, P50/P99 latency
  - CPU/mem/GC basic logs

---

## 10. Self-check

This plan covers all frozen capabilities with at least one test:
- protocol framing ✅
- JSON serializer + SPI ✅
- transport long connection + inflight ✅
- core proxy + dispatch ✅
- governance timeout/retry/trace/log ✅
- registry redis ttl/pubsub ✅
- lb random/rr ✅
- heartbeat/reconnect ✅ (integration tests)
