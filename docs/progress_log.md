# MiniRPC 进度条与工作日志（Beta/E1）

用途：记录 E1 Beta 的进度与工作日志，方便后续阅读与追踪。
来源：当前仓库状态、`docs/chatgpt/06_dev_ready_cn/01_epic_us_task.md`、`docs/chatgpt/05PoC/poc_progress.md`。

E1 进度条：[####------] 40%
总体进度条（E0-E4）：[#---------] 15%（基于代码可见性粗略估算）

## 快照（根据仓库现状推断）
- E0 多模块骨架已搭建（见 `pom.xml`），但 surefire/failsafe 配置未统一。
- E1.1 协议分帧与粘包/半包处理已实现并有测试。
- E1.2 JSON 序列化 SPI 已实现，测试已补齐。
- E1.3 Netty Client/Server 尚未实现。
- E1.4 Core 代理/分发与示例尚未实现。

## PoC 历史摘要
- PoC #1：Netty + 虚拟线程业务 offload 可行，IO 线程不阻塞。
- PoC #2：固定头 22B 的半包/粘包解码验证通过。
- PoC #3/#4：Kryo 在并发场景不安全，需 ThreadLocal/对象池；不进入 1.0。
- PoC #5：WSL 与 Windows 性能差异有记录，缺少可执行脚本。
- PoC #6：长连接 + 背压压测表现稳定，参数可控。

## 工作日志（中文，需记录改动文件与决策过程）

### 2025-??-?? 仓库快照（历史状态，不代表本次改动）
改动概览：
- 协议分帧链路已落地并有测试。
- JSON 序列化 SPI 已落地（ServiceLoader）。

涉及文件（历史已有）：
- 新增/修改：`minirpc-protocol/src/main/java/top/ainexur/minirpc/protocol/codec/frame/FrameEncoder.java`
- 新增/修改：`minirpc-protocol/src/main/java/top/ainexur/minirpc/protocol/codec/frame/FrameParser.java`
- 新增/修改：`minirpc-transport-netty/src/main/java/top/ainexur/minirpc/transport/netty/codec/NettyFrameSlicer.java`
- 新增/修改：`minirpc-transport-netty/src/test/java/top/ainexur/minirpc/transport/netty/codec/NettyFrameSlicerTest.java`
- 新增/修改：`minirpc-serialization/src/main/java/top/ainexur/minirpc/serialization/JsonSerializer.java`
- 新增/修改：`minirpc-serialization/src/main/resources/META-INF/services/top.ainexur.minirpc.serialization.Serializer`

决策与思考：
- 协议侧优先验证半包/粘包，避免后续传输层调试成本扩散。
- 序列化选择 JSON 作为 1.0 默认，Kryo 仅留 PoC 结论，不进入实现范围。

### 2025-??-?? 本次补齐 E1.2 测试与日志（当前改动）
改动目标：
- 让 E1.2 有可自动化验证的最小测试闭环（UT-S1/UT-S2）。
- 新增一份可长期维护的进度与工作日志。

新增/修改文件：
- 新增：`docs/progress_log.md`
- 修改：`minirpc-serialization/pom.xml`
- 新增：`minirpc-serialization/src/test/java/top/ainexur/minirpc/serialization/JsonSerializerTest.java`
- 新增：`minirpc-serialization/src/test/java/top/ainexur/minirpc/serialization/SerializerRegistryTest.java`

关键决策与思考：
- 测试优先覆盖“序列化往返正确性”和“ServiceLoader 可发现性”，这是 E1.2 的最小验收面。
- 为避免与协议层强耦合，测试中使用本地的简单 record 作为序列化对象，而不是直接依赖 `RpcRequest/RpcResponse`；这样在 E1.3/E1.4 尚未完成时仍可独立运行。
- `minirpc-serialization` 之前缺少 JUnit 与 surefire 配置，补齐后可独立执行模块测试，减少跨模块依赖带来的不确定性。

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
