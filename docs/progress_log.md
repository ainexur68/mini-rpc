# MiniRPC 进度条与工作日志（Beta/E1）

用途：记录 E1 Beta 的进度与工作日志，方便后续阅读与追踪。
来源：当前仓库状态、`docs/chatgpt/06_dev_ready_cn/01_epic_us_task.md`、`docs/chatgpt/05PoC/poc_progress.md`。

E1 进度条：[####------] 40%
总体进度条（E0-E4）：[#---------] 15%（基于代码可见性粗略估算）

## 快照（根据仓库现状推断）
- E0 多模块骨架已搭建（见 `pom.xml`），模块依赖方向清晰；测试插件配置未统一（仅部分模块有 surefire）。
- E1.1 协议分帧与粘包/半包处理已实现并有测试（FrameEncoder/FrameParser/NettyFrameSlicer）。
- E1.2 JSON 序列化 SPI 已实现，测试已补齐（JsonSerializer + SerializerRegistry）。
- E1.3 Netty Client/Server 尚未实现（缺 TransportServer/TransportClient/ConnectionManager 及实现类）。
- E1.4 Core 代理/分发与示例尚未实现（缺 ServiceExporter/ProviderDispatcher/ReferenceFactory/FilterChain 与示例应用）。

## PoC 历史摘要
- PoC #1：Netty + 虚拟线程业务 offload 可行，IO 线程不阻塞。
- PoC #2：固定头 22B 的半包/粘包解码验证通过。
- PoC #3/#4：Kryo 在并发场景不安全，需 ThreadLocal/对象池；不进入 1.0。
- PoC #5：WSL 与 Windows 性能差异有记录，缺少可执行脚本。
- PoC #6：长连接 + 背压压测表现稳定，参数可控。

## 工作日志（中文，需记录改动文件与决策过程）

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
