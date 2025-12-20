# MiniRPC 1.0 测试计划（单测 / 集成 / 性能）

> 这是可执行清单：测试类名、断言内容、最小化依赖与环境。

---

## 1. 测试分类

- **单元测试（UT）**：不依赖外部系统（不要求 Redis）。
- **集成测试（IT）**：可能需要外部依赖（Redis）。优先 Testcontainers（如允许）。
- **性能/PoC**：可选微基准或压测工具（不作为 CI 阻断）。

---

## 2. Protocol 测试（`minirpc-protocol`）

### UT-P1 Half packet decode
**类**：`top.ainexur.minirpc.protocol.codec.MiniRpcFrameDecoderHalfPacketTest`

**Given**
- 一帧完整编码字节
- 分 3 段喂入 decoder

**Assert**
- 仅输出 1 个 `MiniRpcFrame`
- 字段匹配（magic/version/requestId/bodyLength）

### UT-P2 Sticky packets decode
**类**：`MiniRpcFrameDecoderStickyPacketTest`

**Given**
- bytes(frame1) + bytes(frame2) 拼接

**Assert**
- 输出 2 帧且顺序正确

### UT-P3 Invalid magic
**类**：`MiniRpcFrameDecoderInvalidMagicTest`

**Assert**
- decoder 抛异常或关闭 channel（需固定行为并写入文档）

---

## 3. Serialization 测试（`minirpc-serialization`）
> 1.0 仅提供 JSON 实现；Kryo 不在 1.0 测试范围内。

### UT-S1 JSON 循环
**类**：`top.ainexur.minirpc.serialization.json.JsonSerializerTest`

**Assert**
- request 序列化/反序列化字段一致
- attachments 保持

### UT-S2 SPI 发现
**类**：`top.ainexur.minirpc.serialization.SerializerRegistryTest`

**Assert**
- `new SerializerRegistry().required((byte)0)` 返回 JsonSerializer

---

## 4. Transport 测试（`minirpc-transport-netty`）

### IT-T1 Server/client basic
**类**：`top.ainexur.minirpc.transport.netty.NettyTransportBasicIT`

**Setup**
- 启动 server（随机端口）与 `RequestHandler` echo
- client 发请求

**Assert**
- future 完成
- response.requestId 与 requestId 相同
- response.code == OK

### IT-T2 Concurrency inflight
**类**：`NettyTransportConcurrencyIT`

**Given**
- 并发 1000 请求
- server handler sleep 1ms（虚拟线程）

**Assert**
- futures 全部完成
- inflight 无泄漏（size 回到 0）

### IT-T3 No business on event loop
**类**：`NettyTransportOffloadIT`

**Assert**
- handler 中记录 `Thread.currentThread()`
- 线程名不包含 `nioEventLoop`（或使用其他明确规则）

---

## 5. Core 测试（`minirpc-core`）

### IT-C1 End-to-end hello
**类**：`top.ainexur.minirpc.core.e2e.HelloServiceE2EIT`

**Setup**
- Provider exporter 注册 HelloServiceImpl
- Server 使用 ProviderDispatcher 作为 RequestHandler
- Consumer 使用 ReferenceFactory

**Assert**
- `hello("x")` 返回预期字符串
- attachments 内包含 traceId（后续由治理固化）

### UT-C2 Method overload resolution
**类**：`ProviderDispatcherOverloadTest`

**Assert**
- paramTypeNames 不同时能解析到正确方法

---

## 6. Governance 测试（`minirpc-governance`）

### UT-G1 Timeout
**类**：`TimeoutFilterTest`

**Given**
- handler sleep 200ms
- timeout 50ms

**Assert**
- 抛 RpcException TIMEOUT

### UT-G2 Retry once
**类**：`RetryFilterTest`

**Given**
- 第一次调用失败（CONNECTION_CLOSED）
- 第二次成功

**Assert**
- 总调用次数 == 2
- 结果成功

### UT-G3 TraceId
**类**：`TraceFilterTest`

**Assert**
- request 无 traceId 时注入一个

---

## 7. Registry 测试（`minirpc-registry-redis`）

### IT-R0 Redis 可用性策略
选项 A（推荐）：**Testcontainers**  
选项 B：本地 Redis `localhost:6379`，并通过 `-Dredis.host/-Dredis.port` 配置

### IT-R1 register + lookup
**类**：`RedisRegistryRegisterLookupIT`

**Assert**
- register 后 lookup 返回实例

### IT-R2 TTL expire
**类**：`RedisRegistryTtlExpireIT`

**Given**
- ttl=2s 注册
- 等待 3s

**Assert**
- lookup 为空

### IT-R3 Pub/Sub 变更通知
**类**：`RedisRegistrySubscribeIT`

**Assert**
- 新注册后回调被触发，列表更新

---

## 8. LoadBalancer 测试（`minirpc-loadbalancer`）

### UT-L1 Random select
**类**：`RandomLoadBalancerTest`  
**Assert**：返回元素属于列表

### UT-L2 RoundRobin
**类**：`RoundRobinLoadBalancerTest`  
**Assert**：按 ServiceKey 轮询 0..n-1

---

## 9. 性能基准（可选，非 CI 阻断）

- 复用 PoC 参数：
  - server：`maxInFlight`, `bizMs`
  - client：total requests, threads, connections
- 输出：
  - 总耗时、QPS、P50/P99 延迟
  - CPU/内存/GC 基本日志

---
