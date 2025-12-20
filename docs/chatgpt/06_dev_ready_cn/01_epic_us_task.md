# MiniRPC 1.0 实施计划（Epic / US / Task）— 开发就绪版

> 范围基线：**MiniRPC_1.0_Requirement_Freeze.md**、**PROTOCOL_Final.md**、**Architecture Review Record**。  
> 目标：开发者（或 AI）可以按 **模块骨架 → 最小垂直切片 → 完整 1.0** 的路径实现，且有清晰验收标准。

---

## 0. 术语与约定

- **Epic**：跨层级的里程碑交付物（例如“垂直切片 MVP#1”）。
- **US（User Story）**：用户可感知的能力（例如“Consumer 可以调用 Provider 并返回结果”）。
- **Task**：可独立检查的代码级事项（类/接口/测试）。

### 每个 Task 的 DoD（完成定义）
一个 Task 只有满足以下条件才算“完成”：
1. **JDK 21** 编译通过。
2. 至少有 **一个自动化测试**（单测/集成）或可运行示例。
3. 具备 **明确的验收检查点**（日志/断言）。
4. 不违反跨层依赖规则（见架构评审）。

---

## 1. Epic E0 — 仓库 / 多模块骨架（必须优先）

### 目标
建立 Maven 多模块工程并约束依赖方向，避免后续演进出现循环依赖。

### US E0.1 — 作为开发者，我可以一条命令构建整个仓库
#### Tasks
- **T0.1** 创建父项目 `minirpc-parent`（packaging `pom`）
  - 验收：`mvn -q -DskipTests package` 成功。
- **T0.2** 创建模块（全部可编译但可为空）：
  - `minirpc-common`（共享工具 + 错误模型）
  - `minirpc-protocol`
  - `minirpc-serialization`
  - `minirpc-transport-netty`
  - `minirpc-registry-redis`
  - `minirpc-loadbalancer`
  - `minirpc-governance`
  - `minirpc-core`
  - `minirpc-example-provider`
  - `minirpc-example-consumer`
  - 验收：每个模块 `src/main/java` 下有至少一个类。
- **T0.3** 依赖方向约束（高层规则）
  - `core` 依赖：`common`, `protocol`, `serialization`, `transport`, `registry`, `loadbalancer`, `governance`
  - `transport-netty` 依赖：`common`, `protocol`, `serialization`
  - `registry-redis` 依赖：`common`
  - `governance` 依赖：`common`, `core-api`（如拆分）或仅 `common` + 接口
  - `protocol` 仅依赖：`common`
  - 验收：不存在“向上层依赖”（例如 `protocol` 不得依赖 `core`）。
- **T0.4** 增加 `maven-surefire-plugin` 与 `maven-failsafe-plugin` 基线配置
  - 验收：`mvn test` 跑单测；`mvn verify` 跑集成测试（如有）。

---

## 2. Epic E1 — 垂直切片 MVP#1（基于 Netty 的请求/响应，无 Registry/LB）

### 目标
打通一次 RPC 调用链路：
`consumer proxy → transport send → provider dispatch → return response`

该阶段验证：
- 协议分帧（固定头 22B + 扩展头跳过 + Body）
- RequestId 的 in-flight 关联
- 服务器端业务 offload 到 Java 21 虚拟线程
- 基础 JSON 序列化（1.0 默认且唯一）

### US E1.1 — 协议分帧可处理粘包/半包
#### Tasks
- **T1.1.1** 固定头常量 + 布局
  - `Magic=0xCAFE`, `Version=1`, 固定头大小=22 bytes
- **T1.1.2** 实现 `MiniRpcFrame` 模型（header + body bytes）
- **T1.1.3** Netty `ByteToMessageDecoder`
  - 先读 22B header
  - 校验 magic/version
  - 按 `HeaderLength` 跳过扩展头
  - 按 `BodyLength` 读取 body
  - 处理半包回退与粘包循环解码
- **T1.1.4** Netty `MessageToByteEncoder`
  - 编码固定头 + 扩展头 + body
- **测试**
  - UT-P1：半包解码（分 2-3 段喂入）
  - UT-P2：粘包解码（两帧拼接）
  - UT-P3：非法 magic 触发异常或关闭（需固定行为）

验收：
- 单测通过；解码确定且不丢数据。

### US E1.2 — 通过 SPI 提供基础 JSON 序列化
#### Tasks
- **T1.2.1** 定义 `Serializer` SPI
- **T1.2.2** 实现 `JsonSerializer`（SerializeType=0）
- **T1.2.3** 提供 `SerializerRegistry`（`ServiceLoader` 加载）
- **测试**
  - UT-S1：request/response JSON 循环
  - UT-S2：ServiceLoader 可发现 JsonSerializer

验收：
- header 中 `SerializeType` 与序列化器一致；循环正确。

### US E1.3 — 基于 Netty 的长连接传输（Client/Server）
#### Tasks
- **T1.3.1** 冻结接口：`TransportServer`, `TransportClient`, `ConnectionManager`
- **T1.3.2** 实现 `NettyTransportServer`
  - Pipeline：FrameDecoder → FrameToMessageDecoder → BusinessHandler → MessageToFrameEncoder
  - 业务处理运行在 **虚拟线程**（不可阻塞 Netty IO 线程）
- **T1.3.3** 实现 `NettyTransportClient`
  - 维护与 Provider 的连接
  - `send(request) -> CompletableFuture<Response>`
  - in-flight 映射 `requestId -> future`
- **T1.3.4** 实现 `SimpleConnectionManager`
  - `Endpoint(host,port)` 维度复用连接
  - 断线重连
- **测试**
  - IT-T1：启动 server（随机端口），client 发请求并收到响应
  - IT-T2：并发 1k 请求，无死锁，future 全部完成
  - IT-T3：server handler 不在 Netty event loop 线程（检查线程名）

验收：
- 一条集成测试本地可在数秒内完成。

### US E1.4 — Core：Proxy + Provider Dispatch（本地内存注册）
#### Tasks
- **T1.4.1** `ServiceExporter`：接口名 → 实现对象
- **T1.4.2** `ProviderDispatcher`：反射调用方法
- **T1.4.3** `ReferenceFactory`：JDK 动态代理构造 `RpcRequest` 并调用 `TransportClient`
- **T1.4.4** Filter chain 最小骨架（可空实现）
- **测试**
  - IT-C1：完整链路 `HelloService#hello("x")` 返回 `"Hello x"`
  - UT-C2：方法重载解析正确（若存在）

验收：
- 示例应用在无 Redis/LB 情况下可运行。

---

## 3. Epic E2 — 治理 MVP（超时 + 重试一次 + TraceId + 日志）

### US E2.1 — 超时控制
Tasks:
- 客户端超时（超时后 future 异常结束）
- 避免 transport 层“重复超时”

Tests:
- UT-G1：server sleep > timeout 触发超时

### US E2.2 — 重试（最多 1 次）
Tasks:
- 仅对可重试错误重试（timeout/connection closed）
- 在上下文/attachments 记录重试次数用于日志

Tests:
- UT-G2：第一次失败（模拟），第二次成功；总尝试次数=2

### US E2.3 — TraceId 注入 + 调用日志
Tasks:
- `TraceFilter` 缺省注入 traceId
- `LoggingFilter` 记录 service/method/cost/requestId/traceId/resultCode

Tests:
- UT-G3：traceId 端到端存在（consumer→provider→consumer）

---

## 4. Epic E3 — Registry（Redis）+ LoadBalancer

### US E3.1 — Redis registry
Tasks:
- `RedisRegistry` 注册 provider（TTL + 续租）
- consumer 启动时拉取 provider 列表
- consumer 订阅 Pub/Sub 获取变化

Tests:
- IT-R1：provider 注册；consumer 可发现
- IT-R2：TTL 过期后移除
- IT-R3：Pub/Sub 更新本地缓存

### US E3.2 — LoadBalancer via SPI
Tasks:
- 接口 `LoadBalancer#select(List<ServiceInstance>, Invocation)`
- 实现：`RandomLoadBalancer`, `RoundRobinLoadBalancer`
- SPI 加载

Tests:
- UT-L1：random 返回在列表内
- UT-L2：RR 按 serviceKey 轮询

---

## 5. Epic E4 — 心跳 + 连接鲁棒性（结构先行）

### US E4.1 — Heartbeat frame（bit0）
Tasks:
- client 空闲时周期性发送心跳
- server 立即响应
- 心跳 body 可为 0

Tests:
- IT-H1：client 发心跳，收到心跳响应

### US E4.2 — Connection recovery
Tasks:
- channel inactive 时，fail 该通道 inflight futures（可重试异常）
- 下次请求触发懒重连

Tests:
- IT-H2：kill server；client future 失败；重启 server 后下一次调用成功

---

## 6. 自检（完整性与准确性）

本计划与 1.0 冻结需求对齐：

- Netty TCP 长连接 ✅
- Request/Response + Future ✅
- Consumer 简单连接池 ✅
- Provider 使用 Java 21 虚拟线程 ✅
- length 字段处理粘包/半包 ✅
- 心跳结构 ✅
- Redis registry + TTL + pub/sub ✅
- LB Random/RR via SPI ✅
- 治理：timeout/retry(once)/trace/log ✅
- 1.0 仅默认 JSON；Kryo 仅作为 PoC 结论，不进入 1.0 交付 ✅

如与冻结需求冲突，优先冻结需求文档。
