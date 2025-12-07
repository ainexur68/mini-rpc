# MiniRPC 架构评审记录（Architecture Review Record v1）

> 评审目标：在进入编码阶段前，对 MiniRPC 1.0 的架构设计进行一次正式评审，确认设计可作为稳定基线（Design Baseline）。

## 1. 评审基本信息

- 项目名称：MiniRPC
- 版本范围：1.0（需求已冻结）
- 评审对象：
  - 《MiniRPC 项目设计文档》
  - 《MiniRPC 1.0 需求冻结文档》
  - 《MiniRPC Protocol Specification（PROTOCOL.md）》
  - 《MiniRPC Architecture Full Edition》
- 本次输出：
  - 架构评审结论与问题列表
  - 风险清单
  - 关键决策确认
  - 对文档的修订项（已回写至相关文档）

## 2. 需求 → 架构对齐情况

### 2.1 总体结论

- 从功能视角看，MiniRPC 1.0 的冻结需求在架构文档中都有对应实现方案：
  - Transport：基于 Netty 的 TCP 长连接、连接池、心跳机制。
  - Protocol：自定义二进制协议（固定头 22B + 可扩展头 + Body）。
  - Registry：Redis 注册中心，支持 TTL + Pub/Sub。
  - LoadBalancer：Random / RoundRobin，通过 SPI 扩展。
  - Governance：超时控制、一次重试、TraceId、调用日志（基于 Filter 链实现）。
  - Core：Invoker / Filter / 动态代理机制。
  - Example：provider/consumer 示例工程。
- 没有发现“需求中有、架构中完全缺失”的模块或能力。

### 2.2 关键一致性点

- 协议头部字段统一为：Magic（0xCAFE）+ Version + SerializeType + Flags(2B) + RequestId + HeaderLength + BodyLength。
- SerializeType 支持 JSON（默认）和 Kryo（可选），并通过数值区间预留扩展空间。
- Flags 为 2 字节短整型，用 bit 位编码：心跳、压缩、加密、单向、响应等属性。
- Registry 采用 Redis，利用 Set + TTL + Pub/Sub 实现服务注册、续期与变更通知。
- 治理能力通过 Filter 责任链挂载在调用路径上，不侵入协议和传输层。

### 2.3 已修复的一致性问题

- 架构文档中原有的协议描述为：`Magic(0xDA,0xBB) + MessageType(1B) + Flags(1B)`，与《需求冻结文档 / PROTOCOL.md》不一致。
- 本次评审已将架构文档的固定头表格修订为与 PROTOCOL.md 完全一致：
  - 删除 `MessageType` 字段。
  - `Flags` 升级为 2 字节 short，采用统一 bit bitmap 方案。
  - 魔数统一为 `0xCAFE`。
- 协议细节以《PROTOCOL_Final.md》为唯一真源，其他文档只引用，不再各自维护一份字段表。

## 3. 模块边界与职责划分评审

### 3.1 分层结构合理性

- 当前分层：
  - core：Invoker、Filter 链、动态代理。
  - protocol：协议封装与编解码。
  - transport：Netty Client/Server、连接池、心跳。
  - registry：服务注册与发现（Redis）。
  - loadbalancer：负载均衡策略（Random / RoundRobin，支持 SPI 扩展）。
  - governance：治理能力（超时、重试、TraceId、日志等）。
  - example：演示工程。
- 评审结论：
  - 分层方式清晰，对标 Dubbo 等成熟框架的通用分层。
  - 各模块均有清晰的对外接口定义，具备可替换性与扩展性。

### 3.2 边界风险与约束

评审中重点指出以下潜在“越界”风险，并在架构文档附录中给出约束：

1. **Filter / 治理层不应直接操作 ByteBuf / 固定头字段**
   - Filter 只操作 `RpcRequest` / `RpcResponse` / `RpcContext` 等抽象对象。
   - TraceId、路由标签等应通过 `attachments` 传递，由 Protocol 模块负责将其映射到 Ext Header。

2. **LoadBalancer 只依赖 ServiceInstance 抽象**
   - 只看到 `ServiceInstance(host, port, metadata)` 和 `Invocation`。
   - 不得直接访问 Redis 客户端或 Netty Channel。

3. **Registry 与 Transport 解耦**
   - Registry 只暴露“服务实例列表 + 变更事件流”，不感知协议细节、传输细节。
   - Transport 不直接依赖 Redis，只消费 `ServiceInstance`。

4. **超时/重试逻辑统一在治理层**
   - 建议以客户端 Future 超时为“业务超时”的唯一来源。
   - Transport 层避免再做一层超时逻辑，以免与治理层冲突。

上述约束已通过《MiniRPC Architecture Full Edition》的“附录 12.4 模块依赖与边界约束”落地。

## 4. 协议、异常处理与生命周期评审

### 4.1 协议层

- 固定头 22 字节，字段含义与长度在需求、协议、架构文档中已经完全统一。
- Flags 采用 16-bit bitmap 设计，支持：心跳、压缩、加密、单向、响应等标志。
- 心跳帧沿用相同帧格式，通过 Flags 中的 HEARTBEAT 位标识，Body 可以为空（BodyLength=0）。
- Ext Header 在 1.0 中允许为 0，但协议和实现均需支持跳过/解析，为后续 TraceId / 路由扩展预留空间。

### 4.2 错误码与异常处理

- 《PROTOCOL_Final.md》中定义了一致的错误码集合：
  - OK、TIMEOUT、SERIALIZE_ERROR、DESERIALIZE_ERROR、UNSUPPORTED_SERIALIZE_TYPE、INTERNAL_SERVER_ERROR、PROTOCOL_ERROR、CONNECTION_CLOSED 等。
- 本次评审在架构文档中新增了“错误码与异常映射表”，明确：
  - 不同层（Serializer/Protocol/Transport/业务）遇到异常时如何映射到统一错误码。
  - 客户端对外只暴露少量业务含义清晰的异常类型，内部再根据错误码区分细节。

### 4.3 调用生命周期与注册中心行为

- 在架构文档附录中补充了“调用生命周期（Client 侧）”与 “Provider 生命周期与注册中心交互”。
- 核心共识：
  - 调用超时后，客户端从 inflight 表中删除 Future，迟到响应仅做日志记录并丢弃。
  - Provider 优雅下线时应主动：
    1. 从 Redis 服务集合中删除自身实例；
    2. 通过 Pub/Sub 发送一次 OFFLINE 通知；
    3. 再关闭 Netty Server。
  - TTL 机制作为兜底，不作为唯一的摘除手段。
  - Consumer 一方面通过 Pub/Sub 感知变更，一方面可定期全量拉取以应对通知丢失。

## 5. 风险列表

本次评审识别出以下主要风险，并给出应对策略：

1. **Redis 注册中心 TTL 设计风险**
   - 心跳续期若受 GC / STW / 网络抖动影响，可能导致实例被误删。
   - 应对：缩短心跳间隔（TTL 的 1/3 左右）、优雅下线时主动删除、Consumer 定期全量拉取。

2. **协议多版本/多源风险**
   - 架构文档与协议文档曾出现不一致描述（魔数、字段表结构不同）。
   - 应对：以 PROTOCOL.md 为唯一真源，其他文档只引用；本次已完成修订。

3. **Kryo 反序列化安全风险**
   - Kryo 存在潜在反序列化攻击风险，1.0 不做安全加固。
   - 应对：默认仅开启 JSON；Kryo 作为可选高性能实现，在文档中明确“仅建议在可信内网用于学习/压测”。

4. **缺乏 TLS/鉴权的安全风险**
   - 需求中明确 1.0 不做 TLS/鉴权。
   - 应对：在需求与架构文档中清晰标注“适用范围为可信内网 + 学习/演示场景，不建议用于生产公网环境”。

5. **重试导致的幂等性与雪崩风险**
   - 若接口非幂等，且启用了自动重试，可能产生重复写操作；在高延迟场景下可能放大 Provider 压力。
   - 应对：重试策略应与接口幂等性声明绑定；重试次数与退避策略可配置。

6. **虚拟线程使用带来的资源风险**
   - 虚拟线程虽轻量，但底层仍依赖 carrier 线程池，高 QPS 下可能带来 CPU/内存压力。
   - 应对：在治理层预留简单的并发上限/限流能力，在压测阶段关注虚拟线程数量与资源使用。

## 6. 关键决策确认

本次评审确认并记录以下关键架构决策，作为后续设计变更的裁决依据：

1. **协议层：**
   - 固定头采用 22 字节格式，字段顺序与含义与 PROTOCOL.md 一致。
   - `Flags` 使用 16-bit bitmap，不再使用单独的 `MessageType` 字段。
   - Ext Header 在 1.0 允许为空，但 Decoder/Encoder 必须正确处理。

2. **传输层与线程模型：**
   - 使用 Netty NIO + Java 21 虚拟线程执行 Provider 业务逻辑。
   - IO 线程只负责编解码，不执行耗时业务。

3. **序列化策略：**
   - 默认使用 JSON 序列化。
   - Kryo 以 SPI 形式提供，可选开启，用于性能实验。

4. **注册中心：**
   - 1.0 仅支持基于 Redis 的注册中心实现。
   - Nacos/ZooKeeper 等作为未来演进方向，不在 1.0 范围内。

5. **负载均衡：**
   - 必须提供 Random 与 RoundRobin 两种策略。
   - LoadBalancer 运行在客户端侧，基于 ServiceInstance 进行选择，并支持 SPI 扩展。

6. **治理能力：**
   - 1.0 必须具备：超时控制、一次重试、TraceId 注入、调用日志记录。
   - 熔断、限流、指标采集等能力可延后至后续版本。

7. **安全能力：**
   - 1.0 明确不支持 TLS/SSL 与鉴权机制。
   - 文档中标明“仅适用于可信内网学习与演示”。

## 7. 评审结论

- 在修复协议字段不一致问题、补充错误码映射与生命周期说明、明确模块依赖边界后，当前架构设计**可以作为 MiniRPC 1.0 的设计基线**。  
- 后续开发阶段若涉及以下变更：
  - 修改协议头字段或含义；
  - 引入新的错误码；
  - 引入新的跨模块依赖；
  - 引入 TLS/鉴权等安全能力；
  必须重新发起变更评审。

本记录作为 MiniRPC 架构评审的正式产物，与需求冻结文档、协议文档、架构文档一起构成设计基线。
