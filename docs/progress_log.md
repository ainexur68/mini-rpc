# MiniRPC 进度条与工作日志（Beta/E1）

用途：记录 E1 Beta 的进度与工作日志，方便后续阅读与追踪。
来源：当前仓库状态、`docs/chatgpt/06_dev_ready_cn/01_epic_us_task.md`、`docs/chatgpt/05PoC/poc_progress.md`。

E1 进度条：[#########-] 90%
总体进度条（E0-E4）：[###-------] 30%（基于代码可见性粗略估算）

## 快照（根据仓库现状推断）
- E0 多模块骨架已搭建（见 `pom.xml`），模块依赖方向清晰；测试插件配置未统一（仅部分模块有 surefire）。
- E1.1 协议分帧与粘包/半包处理已实现并有测试（FrameEncoder/FrameParser/NettyFrameSlicer）。
- E1.2 JSON 序列化 SPI 已实现，测试已补齐（JsonSerializer + SerializerRegistry）。
- E1.3 Netty Client/Server 已实现并有集成测试。
- E1.4 Core 代理/分发与示例已实现并有端到端测试。

## PoC 历史摘要
- PoC #1：Netty + 虚拟线程业务 offload 可行，IO 线程不阻塞。
- PoC #2：固定头 22B 的半包/粘包解码验证通过。
- PoC #3/#4：Kryo 在并发场景不安全，需 ThreadLocal/对象池；不进入 1.0。
- PoC #5：WSL 与 Windows 性能差异有记录，缺少可执行脚本。
- PoC #6：长连接 + 背压压测表现稳定，参数可控。

## 工作日志（中文，需记录改动文件与决策过程）

### 2026-01-04 回退协议层半包/粘包单测补丁（本次改动）
改动目标：
- 由于 Netty 侧已有半包/粘包覆盖，协议层测试不再重复扩展。

新增/修改文件：
- 修改：`minirpc-protocol/src/test/java/top/ainexur/minirpc/protocol/codec/frame/FrameParserTest.java`

改动细节：
- 移除 `rejectHalfPacket` 与 `rejectStickyPacket` 两个协议层单测与对应 `concat` 辅助方法。

自测：
- `mvn -pl minirpc-protocol test`

### 2026-01-02 仓库快照（历史状态，不代表本次改动）
背景与目标：
- PoC 阶段结束后，进入 1.0/E1 实现准备；优先落地协议分帧、序列化 SPI 作为纵向链路的底座。
- 按冻结需求，1.0 仅默认 JSON，Kryo 仅保留 PoC 结论，不进入交付范围。

改动概览：
- 协议分帧链路已落地并有测试：Frame 编码、解析、Netty 切帧。
- JSON 序列化 SPI 已落地：实现 JsonSerializer 并通过 ServiceLoader 注册。

涉及文件（历史已有）：
- 新增/修改：`minirpc-protocol/src/main/java/top/ainexur/minirpc/protocol/codec/frame/FrameEncoder.java`
- 新增/修改：`minirpc-protocol/src/main/java/top/ainexur/minirpc/protocol/codec/frame/FrameParser.java`
- 新增/修改：`minirpc-protocol/src/main/java/top/ainexur/minirpc/protocol/frame/MiniRpcFrame.java`
- 新增/修改：`minirpc-protocol/src/main/java/top/ainexur/minirpc/protocol/MiniRpcProtocol.java`
- 新增/修改：`minirpc-transport-netty/src/main/java/top/ainexur/minirpc/transport/netty/codec/NettyFrameSlicer.java`
- 新增/修改：`minirpc-transport-netty/src/main/java/top/ainexur/minirpc/transport/netty/codec/NettyFrameEncoder.java`
- 新增/修改：`minirpc-transport-netty/src/test/java/top/ainexur/minirpc/transport/netty/codec/NettyFrameSlicerTest.java`
- 新增/修改：`minirpc-serialization/src/main/java/top/ainexur/minirpc/serialization/Serializer.java`
- 新增/修改：`minirpc-serialization/src/main/java/top/ainexur/minirpc/serialization/JsonSerializer.java`
- 新增/修改：`minirpc-serialization/src/main/java/top/ainexur/minirpc/serialization/SerializerRegistry.java`
- 新增/修改：`minirpc-serialization/src/main/resources/META-INF/services/top.ainexur.minirpc.serialization.Serializer`

决策与思考：
- 先做协议分帧和粘包/半包测试，是为了锁定协议字节级正确性，避免传输层与业务层混杂调试。
- 选择 JSON 作为默认序列化是与冻结需求对齐，同时减少早期实现风险；Kryo 的并发问题已在 PoC 暴露，因此不纳入 1.0 实现。
- 序列化 SPI 采用 ServiceLoader，便于未来扩展而不增加 core 依赖复杂度。

### 2026-01-02 本次补齐 E1.2 测试与日志（当前改动）
改动目标：
- 让 E1.2 有可自动化验证的最小测试闭环（UT-S1/UT-S2）。
- 建立统一的进度与工作日志，记录决策依据与修改内容。

新增/修改文件：
- 新增：`docs/progress_log.md`
- 修改：`minirpc-serialization/pom.xml`
- 新增：`minirpc-serialization/src/test/java/top/ainexur/minirpc/serialization/JsonSerializerTest.java`
- 新增：`minirpc-serialization/src/test/java/top/ainexur/minirpc/serialization/SerializerRegistryTest.java`

改动细节：
- `minirpc-serialization/pom.xml`：
  - 增加 JUnit 5 依赖，保证测试可运行。
  - 增加 surefire 插件配置，使模块可独立执行单测。
- `JsonSerializerTest`：
  - 增加“请求/响应”往返测试（UT-S1），覆盖序列化与反序列化最小闭环。
  - 使用本地 record 作为测试 DTO，避免对 protocol/core 的强耦合。
- `SerializerRegistryTest`：
  - 增加 ServiceLoader 可发现性测试（UT-S2），验证 `META-INF/services` 注册是否有效。

关键决策与思考：
- 测试优先覆盖“往返正确性”和“ServiceLoader 发现性”，这是 E1.2 的最小验收面；复杂对象序列化不在此阶段扩展，避免扩大测试面。
- 使用本地测试 DTO 的原因：
  - 当前 E1.3/E1.4 未完成，避免引入对请求模型演进的额外耦合。
  - JsonSerializer 只需验证对任意 POJO 的正确性，满足 SPI 的最小能力证明。
- 先补测试再推进 E1.3，是为了确保“协议与序列化”两块基础能力可重复验证，减少后续联调的不确定性。

### 2026-01-02 协议解析拆分为 Protocol + Netty 切帧（本次改动）
改动目标：
- 明确分层边界：Netty 只负责凑齐一帧字节，Protocol 只负责解析/编码帧语义。
- 为后续替换传输层或复用协议解析提供基础。
- 保持 MessageCodec 与上层流程不变，只替换 Frame 解析入口。

新增/修改文件（Protocol 层）：
- 新增：`minirpc-protocol/src/main/java/top/ainexur/minirpc/protocol/codec/frame/FrameParser.java`
- 新增：`minirpc-protocol/src/main/java/top/ainexur/minirpc/protocol/codec/frame/FrameEncoder.java`
- 删除：`minirpc-protocol/src/main/java/top/ainexur/minirpc/protocol/codec/decoder/MiniRpcFrameDecoder.java`
- 删除：`minirpc-protocol/src/main/java/top/ainexur/minirpc/protocol/codec/encoder/MiniRpcFrameEncoder.java`
- 修改：`minirpc-protocol/src/main/java/top/ainexur/minirpc/protocol/codec/impl/DefaultMessageCodec.java`
- 修改：`minirpc-protocol/pom.xml`（移除 Netty 依赖）

新增/修改文件（Netty 适配层）：
- 新增：`minirpc-transport-netty/src/main/java/top/ainexur/minirpc/transport/netty/codec/NettyFrameSlicer.java`
- 新增：`minirpc-transport-netty/src/main/java/top/ainexur/minirpc/transport/netty/codec/NettyFrameEncoder.java`
- 修改：`minirpc-transport-netty/pom.xml`（加入 Netty 与测试依赖）

新增/修改文件（测试）：
- 新增：`minirpc-protocol/src/test/java/top/ainexur/minirpc/protocol/codec/frame/FrameParserTest.java`
- 删除：`minirpc-protocol/src/test/java/top/ainexur/minirpc/protocol/decoder/MiniRpcFrameDecoderTest.java`
- 新增：`minirpc-transport-netty/src/test/java/top/ainexur/minirpc/transport/netty/codec/NettyFrameSlicerTest.java`

新增/修改文件（文档）：
- 修改：`docs/chatgpt/06_dev_ready_cn/02_code_blueprint.md`
- 修改：`docs/chatgpt/06_dev_ready/02_code_blueprint.md`

关键决策与思考：
- Netty 侧仅做固定头长度判断与切片，避免在 IO 层掺入协议语义；解析与校验集中在 `FrameParser`。
- `FrameParser` 接受完整帧字节，保证长度一致性（固定头 + extHeader + body），避免解析时隐式依赖 ByteBuf 的 readerIndex。
- 旧的 `MiniRpcFrameDecoder/Encoder` 属于“协议 + Netty 耦合”，为减少传播，直接删除并用新入口替换。
- `DefaultMessageCodec` 增加可配置序列化类型构造参数，为后续扩展序列化实现留出入口。

自测：
- `mvn -pl minirpc-serialization,minirpc-protocol,minirpc-transport-netty -am test`

### 2026-01-02 启动 E1.3 传输层最小实现与测试
改动目标：
- 打通 Netty 长连接传输的最小闭环（请求发出→服务端处理→响应返回）。
- 提供可复用的传输接口，保证 E1.4 能只依赖接口而非 Netty 细节。
- 增加集成测试覆盖（请求响应、并发、虚拟线程 offload）。

新增/修改文件：
- 新增：`minirpc-transport-netty/src/main/java/top/ainexur/minirpc/transport/Endpoint.java`
- 新增：`minirpc-transport-netty/src/main/java/top/ainexur/minirpc/transport/TransportClient.java`
- 新增：`minirpc-transport-netty/src/main/java/top/ainexur/minirpc/transport/TransportServer.java`
- 新增：`minirpc-transport-netty/src/main/java/top/ainexur/minirpc/transport/ConnectionManager.java`
- 新增：`minirpc-transport-netty/src/main/java/top/ainexur/minirpc/transport/RequestHandler.java`
- 新增：`minirpc-transport-netty/src/main/java/top/ainexur/minirpc/transport/netty/NettyTransportClient.java`
- 新增：`minirpc-transport-netty/src/main/java/top/ainexur/minirpc/transport/netty/NettyTransportServer.java`
- 新增：`minirpc-transport-netty/src/main/java/top/ainexur/minirpc/transport/netty/SimpleConnectionManager.java`
- 新增：`minirpc-transport-netty/src/main/java/top/ainexur/minirpc/transport/netty/RequestInFlight.java`
- 新增：`minirpc-transport-netty/src/main/java/top/ainexur/minirpc/transport/netty/codec/NettyMessageDecoder.java`
- 新增：`minirpc-transport-netty/src/main/java/top/ainexur/minirpc/transport/netty/codec/NettyMessageEncoder.java`
- 新增：`minirpc-transport-netty/src/main/java/top/ainexur/minirpc/transport/netty/handler/ClientResponseHandler.java`
- 新增：`minirpc-transport-netty/src/main/java/top/ainexur/minirpc/transport/netty/handler/ServerRequestHandler.java`
- 新增：`minirpc-transport-netty/src/test/java/top/ainexur/minirpc/transport/netty/NettyTransportIntegrationTest.java`

改动细节：
- 定义了传输层基础接口（TransportClient/TransportServer/ConnectionManager/RequestHandler）与 Endpoint 模型。
- Netty 客户端：
  - 通过 DefaultMessageCodec + Frame 编解码，完成对象与字节的转换。
  - 维护 inflight 映射，保证 requestId 能匹配响应并完成 future。
- Netty 服务端：
  - 使用虚拟线程执行 RequestHandler，避免阻塞 Netty IO 线程。
  - 统一在异常时返回 SERVER_ERROR 响应。
- 集成测试：
  - 请求响应闭环（IT-T1）。
  - 并发 1k 请求完成性（IT-T2）。
  - 验证业务处理线程为虚拟线程（IT-T3）。
- 修正服务端写回响应的 outbound 起点：
  - 从 `ctx.writeAndFlush` 改为 `ctx.channel().writeAndFlush`，确保 outbound 经过 MessageEncoder 再到 FrameEncoder。
- IT-T3 校验方式调整：
  - 由线程名包含 “VirtualThread” 改为 `Thread.currentThread().isVirtual()`，避免 JVM 实现差异导致的误判。

关键决策与思考：
- 传输层接口先落地在 transport 模块，E1.4 可以直接依赖接口而不耦合 Netty 实现。
- Pipeline 顺序刻意保证“消息编码 → 帧编码”的 outbound 顺序，避免对象直接落到 FrameEncoder。
- RequestInFlight 仅处理 requestId 与 future 的映射，超时/重试留给 E2 治理阶段统一处理。

验证记录：
- `mvn -pl minirpc-transport-netty -am test` 通过（包含 NettyTransportIntegrationTest 与 NettyFrameSlicerTest）。

### 2026-01-02 完成 E1.4 Core 代理/分发最小链路
改动目标：
- 落地 Core 侧 ServiceExporter / ProviderDispatcher / ReferenceFactory。
- 增加最小 Filter 链骨架与端到端集成测试。
- 提供可运行的 provider/consumer 示例。

新增/修改文件：
- 新增：`minirpc-core/src/main/java/top/ainexur/minirpc/core/Invocation.java`
- 新增：`minirpc-core/src/main/java/top/ainexur/minirpc/core/filter/Filter.java`
- 新增：`minirpc-core/src/main/java/top/ainexur/minirpc/core/filter/FilterChain.java`
- 新增：`minirpc-core/src/main/java/top/ainexur/minirpc/core/filter/DefaultFilterChain.java`
- 新增：`minirpc-core/src/main/java/top/ainexur/minirpc/core/provider/ServiceExporter.java`
- 新增：`minirpc-core/src/main/java/top/ainexur/minirpc/core/provider/ProviderDispatcher.java`
- 新增：`minirpc-core/src/main/java/top/ainexur/minirpc/core/consumer/ReferenceFactory.java`
- 修改：`minirpc-core/pom.xml`
- 新增：`minirpc-core/src/test/java/top/ainexur/minirpc/core/EndToEndTest.java`
- 修改：`minirpc-example-provider/pom.xml`
- 修改：`minirpc-example-consumer/pom.xml`
- 修改：`minirpc-example-provider/src/main/java/top/ainexur/minirpc/example/provider/Main.java`
- 新增：`minirpc-example-provider/src/main/java/top/ainexur/minirpc/example/provider/service/HelloService.java`
- 新增：`minirpc-example-provider/src/main/java/top/ainexur/minirpc/example/provider/service/HelloServiceImpl.java`
- 修改：`minirpc-example-consumer/src/main/java/top/ainexur/minirpc/example/consumer/Main.java`
- 新增：`minirpc-example-consumer/src/main/java/top/ainexur/minirpc/example/provider/service/HelloService.java`

改动细节：
- ServiceExporter 以接口名为 key 管理实现对象，ProviderDispatcher 负责反射调用并封装响应。
- ReferenceFactory 使用 JDK 动态代理构造 RpcRequest，并通过 FilterChain -> TransportClient 发送请求。
- Provider 侧通过 RequestHandler 包装 ProviderDispatcher；Consumer 侧通过 ReferenceFactory 调用示例服务。
- 引入最小 Filter 链结构，便于后续治理能力（超时/重试/Trace）挂接。

关键决策与思考：
- FilterChain 先做“可用最小实现”，只保证顺序执行与终止执行器，避免过早设计。
- 示例接口不单独新建模块，暂用 provider/consumer 各自定义同名接口以保持对齐。
- 当响应非 OK 时，优先透出服务端错误信息，降低联调成本。

验证记录：
- `mvn -pl minirpc-core -am test` 通过（包含 EndToEndTest）。

归档输出（按固定模板）：
- 将修改的文件列表：
  - docs/progress_log.md
  - docs/steps_checklist.md
  - minirpc-core/pom.xml
  - minirpc-core/src/main/java/top/ainexur/minirpc/core/Invocation.java
  - minirpc-core/src/main/java/top/ainexur/minirpc/core/consumer/ReferenceFactory.java
  - minirpc-core/src/main/java/top/ainexur/minirpc/core/filter/DefaultFilterChain.java
  - minirpc-core/src/main/java/top/ainexur/minirpc/core/filter/Filter.java
  - minirpc-core/src/main/java/top/ainexur/minirpc/core/filter/FilterChain.java
  - minirpc-core/src/main/java/top/ainexur/minirpc/core/provider/ProviderDispatcher.java
  - minirpc-core/src/main/java/top/ainexur/minirpc/core/provider/ServiceExporter.java
  - minirpc-core/src/test/java/top/ainexur/minirpc/core/EndToEndTest.java
  - minirpc-example-consumer/pom.xml
  - minirpc-example-consumer/src/main/java/top/ainexur/minirpc/example/consumer/Main.java
  - minirpc-example-consumer/src/main/java/top/ainexur/minirpc/example/provider/service/HelloService.java
  - minirpc-example-provider/pom.xml
  - minirpc-example-provider/src/main/java/top/ainexur/minirpc/example/provider/Main.java
  - minirpc-example-provider/src/main/java/top/ainexur/minirpc/example/provider/service/HelloService.java
  - minirpc-example-provider/src/main/java/top/ainexur/minirpc/example/provider/service/HelloServiceImpl.java
- 每个文件的修改点（bullet）：
  - docs/progress_log.md：记录 E1.4 完成情况、决策、验证与归档信息
  - docs/steps_checklist.md：标记 E1.4 任务完成
  - minirpc-core/pom.xml：引入 JUnit 依赖与 surefire 配置
  - minirpc-core/src/main/java/top/ainexur/minirpc/core/Invocation.java：新增调用上下文对象
  - minirpc-core/src/main/java/top/ainexur/minirpc/core/consumer/ReferenceFactory.java：动态代理构造请求并发送，增强错误透出
  - minirpc-core/src/main/java/top/ainexur/minirpc/core/filter/DefaultFilterChain.java：实现顺序过滤器链
  - minirpc-core/src/main/java/top/ainexur/minirpc/core/filter/Filter.java：定义过滤器接口
  - minirpc-core/src/main/java/top/ainexur/minirpc/core/filter/FilterChain.java：定义过滤器链接口
  - minirpc-core/src/main/java/top/ainexur/minirpc/core/provider/ProviderDispatcher.java：反射分发并封装响应
  - minirpc-core/src/main/java/top/ainexur/minirpc/core/provider/ServiceExporter.java：注册与获取服务实现
  - minirpc-core/src/test/java/top/ainexur/minirpc/core/EndToEndTest.java：端到端链路与异常路径测试
  - minirpc-example-consumer/pom.xml：增加对 core 依赖
  - minirpc-example-consumer/src/main/java/top/ainexur/minirpc/example/consumer/Main.java：消费端调用示例
  - minirpc-example-consumer/src/main/java/top/ainexur/minirpc/example/provider/service/HelloService.java：示例接口（consumer 侧）
  - minirpc-example-provider/pom.xml：增加对 core 依赖
  - minirpc-example-provider/src/main/java/top/ainexur/minirpc/example/provider/Main.java：提供端启动示例
  - minirpc-example-provider/src/main/java/top/ainexur/minirpc/example/provider/service/HelloService.java：示例接口（provider 侧）
  - minirpc-example-provider/src/main/java/top/ainexur/minirpc/example/provider/service/HelloServiceImpl.java：示例实现
- 风险点与测试点：
  - 风险：consumer/provider 使用重复包名接口，未抽公共 API 模块
  - 风险：ReferenceFactory 使用 join 阻塞，后续治理需替换为超时/异步
  - 风险：ProviderDispatcher 反射调用未做缓存
  - 测试：`mvn -pl minirpc-core -am test`
