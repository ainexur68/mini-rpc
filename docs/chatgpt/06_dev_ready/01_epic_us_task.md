# MiniRPC 1.0 Implementation Plan (Epic / US / Task) — Dev-Ready

> Scope baseline: **MiniRPC_1.0_Requirement_Freeze.md**, **PROTOCOL_Final.md**, **Architecture Review Record**.  
> Target: A developer (or AI) can implement **module skeleton → minimal vertical slice → full 1.0** with clear acceptance criteria.

---

## 0. Terminology & Conventions

- **Epic**: A milestone deliverable across layers (e.g., “Vertical Slice MVP#1”).
- **US (User Story)**: A user-observable capability (e.g., “Consumer can call Provider and get response”).
- **Task**: A code-level item (class/interface/test) that is independently checkable.

### Definition of Done (DoD) for each Task
A task is “done” only if:
1. Code compiles on **JDK 21**.
2. Has at least **one automated test** (unit/integration) or a runnable example.
3. Has **explicit acceptance checks** (log/assertions).
4. No cross-layer dependency violations (see Architecture Review).

---

## 1. Epic E0 — Repository / Multi-module Skeleton (must be first)

### Goal
Create a Maven multi-module repo and enforce dependency directions so later tasks don’t devolve into cyclic imports.

### US E0.1 — As a developer, I can build the whole repo with one command
#### Tasks
- **T0.1** Create parent Maven project `minirpc-parent` (packaging `pom`)
  - Acceptance: `mvn -q -DskipTests package` succeeds.
- **T0.2** Create modules (all empty but compilable):
  - `minirpc-common` (shared utils + error model)
  - `minirpc-protocol`
  - `minirpc-serialization`
  - `minirpc-transport-netty`
  - `minirpc-registry-redis`
  - `minirpc-loadbalancer`
  - `minirpc-governance`
  - `minirpc-core`
  - `minirpc-example-provider`
  - `minirpc-example-consumer`
  - Acceptance: each module has a minimal `src/main/java` package with one class.
- **T0.3** Enforce dependency direction (high-level rule)
  - `core` depends on: `common`, `protocol`, `serialization`, `transport`, `registry`, `loadbalancer`, `governance`
  - `transport-netty` depends on: `common`, `protocol`, `serialization`
  - `registry-redis` depends on: `common`
  - `governance` depends on: `common`, `core-api`(if split) OR `common` only + interfaces
  - `protocol` depends only on: `common`
  - Acceptance: no module depends on “up” layers (e.g., `protocol` must not depend on `core`).
- **T0.4** Add `maven-surefire-plugin`, `maven-failsafe-plugin` baseline config
  - Acceptance: `mvn test` runs unit tests; `mvn verify` runs integration tests (if any).

---

## 2. Epic E1 — Vertical Slice MVP#1 (Request/Response over Netty, no Registry/LB yet)

### Goal
Get one RPC call running end-to-end:
`consumer proxy → transport send → provider dispatch → return response`

This validates:
- Protocol framing (22B fixed header + ext header skip + body)
- Inflight correlation via RequestId
- Server offloading business to Java 21 virtual threads
- Basic JSON serialization (1.0 default), Kryo behind SPI (optional)

### US E1.1 — Protocol framing works for sticky/half packets
#### Tasks
- **T1.1.1** Implement fixed header constants + layout
  - `Magic=0xCAFE`, `Version=1`, fixed header size=22 bytes
- **T1.1.2** Implement `MiniRpcFrame` model (header + body bytes)
- **T1.1.3** Netty `ByteToMessageDecoder`:
  - read 22B header first
  - validate magic/version
  - skip ext header by `HeaderLength`
  - read body by `BodyLength`
  - support re-entry (half packet) and loop decode (sticky packet)
- **T1.1.4** Netty `MessageToByteEncoder`:
  - encode fixed header + ext header + body bytes
- **Tests**
  - UT-P1: half packet decode (feed bytes in 2-3 chunks)
  - UT-P2: sticky packets decode (2 frames concatenated)
  - UT-P3: invalid magic closes channel or throws (as configured)

Acceptance:
- Unit tests pass; decoding is deterministic and does not lose bytes.

### US E1.2 — Basic JSON serializer via SPI
#### Tasks
- **T1.2.1** Define `Serializer` SPI
- **T1.2.2** Implement `JsonSerializer` (SerializeType=0)
- **T1.2.3** Provide `SerializerRegistry` (load via `ServiceLoader`)
- **Tests**
  - UT-S1: request/response round-trip JSON
  - UT-S2: ServiceLoader finds JsonSerializer

Acceptance:
- `SerializeType` written in header matches serializer type; round-trip correct.

### US E1.3 — Transport client/server over long-lived TCP (Netty)
#### Tasks
- **T1.3.1** Define required interfaces (frozen): `TransportServer`, `TransportClient`, `ConnectionManager`
- **T1.3.2** Implement `NettyTransportServer`
  - Pipeline: FrameDecoder → FrameToMessageDecoder → BusinessHandler → MessageToFrameEncoder
  - Business execution runs on **virtual threads** (do not block Netty IO thread)
- **T1.3.3** Implement `NettyTransportClient`
  - maintains channel(s) to provider
  - `send(request) -> CompletableFuture<Response>`
  - inflight map `requestId -> future`
- **T1.3.4** Implement `SimpleConnectionManager`
  - keyed by `Endpoint(host,port)` to a channel/connection
  - provides reuse + reconnect on failure
- **Tests**
  - IT-T1: start server on random port, client sends request, gets response
  - IT-T2: concurrency: 1k requests; no deadlocks; futures complete
  - IT-T3: server handler does not run on Netty event loop thread (assert thread name)

Acceptance:
- A single integration test can run locally and complete in seconds.

### US E1.4 — Core: Proxy + Provider dispatch (local in-memory registry)
#### Tasks
- **T1.4.1** `ServiceExporter`: map interface name → impl instance
- **T1.4.2** `ProviderDispatcher`: reflect invoke method by name + param types
- **T1.4.3** `ReferenceFactory`: JDK dynamic proxy that builds `RpcRequest` and calls `TransportClient`
- **T1.4.4** Minimal Filter chain skeleton (empty chain ok for MVP#1)
- **Tests**
  - IT-C1: full flow HelloService#hello("x") returns "Hello x"
  - UT-C2: method resolution works with overload (if any)

Acceptance:
- Example app works without Redis and LB.

---

## 3. Epic E2 — Governance MVP (Timeout + Retry(once) + TraceId + Logs)

### US E2.1 — Timeout control
Tasks:
- Implement client-side timeout (future completes exceptionally on timeout)
- Ensure transport does not “double timeout” (single source of truth)

Tests:
- UT-G1: timeout triggers when server sleeps longer than timeout

### US E2.2 — Retry (max 1)
Tasks:
- Retry only on retryable errors (timeout/connection closed)
- Mark retry count in context/attachments for logging

Tests:
- UT-G2: first attempt fails (simulate), second succeeds; total attempts==2

### US E2.3 — TraceId injection + call logs
Tasks:
- `TraceFilter` injects `traceId` if absent
- `LoggingFilter` logs service/method/cost/requestId/traceId/resultCode

Tests:
- UT-G3: traceId exists end-to-end (consumer→provider→consumer)

---

## 4. Epic E3 — Registry (Redis) + LoadBalancer

### US E3.1 — Redis registry
Tasks:
- `RedisRegistry` provider register with TTL + renewal
- consumer pulls provider list at startup
- consumer subscribes Pub/Sub for change updates

Tests:
- IT-R1: provider registers; consumer discovers
- IT-R2: TTL expires -> provider removed
- IT-R3: pub/sub updates local cache

### US E3.2 — LoadBalancer via SPI
Tasks:
- interface `LoadBalancer#select(List<ServiceInstance>, Invocation)`
- implementations: `RandomLoadBalancer`, `RoundRobinLoadBalancer`
- SPI loader

Tests:
- UT-L1: random returns element within list
- UT-L2: RR cycles per service key

---

## 5. Epic E4 — Heartbeat + Connection robustness (structure support required)

### US E4.1 — Heartbeat frame (bit0)
Tasks:
- client periodically sends heartbeat when idle
- server responds immediately
- heartbeat body length can be 0

Tests:
- IT-H1: client sends heartbeat, gets heartbeat response

### US E4.2 — Connection recovery
Tasks:
- on channel inactive, fail inflight futures with retryable exception
- reconnect lazy on next request

Tests:
- IT-H2: kill server; client future fails; restart server; next call succeeds

---

## 6. Self-check (Completeness & Accuracy)

This plan is aligned to 1.0 frozen requirements:

- Netty TCP long connection ✅
- Request/Response model with Future ✅
- Consumer simple connection pool ✅
- Provider uses Java 21 virtual threads ✅
- sticky/half packets via length fields ✅
- heartbeat structure ✅
- Redis registry + TTL + pub/sub ✅
- LB random/RR via SPI ✅
- governance: timeout/retry(once)/trace/log ✅
- JSON default + Kryo via SPI (optional) ✅

If any future change conflicts with the freeze doc, defer to the freeze doc.
