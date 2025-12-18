# MiniRPC PoC 进度记录（poc_progress.md）

> 最近更新时间：2025-12-08  
> 说明：本文件用于在新 team 中快速恢复 MiniRPC 的 PoC 进度与上下文，作为架构设计与实现阶段之间的“缓冲层”。

---

## 1. 基线状态概览

- 设计文档：《MiniRPC_Design_Document.md》——已完成并作为项目背景与总体设计真源。
- 需求文档：《MiniRPC_1.0_Requirement_Freeze.md》——1.0 版本需求已冻结，不再随意新增需求。
- 协议文档：《PROTOCOL_Final.md》——固定头 22 字节 + 可扩展头 + Body 的协议已定版。
- 架构文档：《MiniRPC_Architecture_Full.md》——分层架构、调用链路、模块边界已经确定。
- 架构评审：《MiniRPC_Architecture_Review_Record_v1.md》——评审通过，关键修改已经回写到上述文档。

当前总体状态：**“文档与架构基线已确定，正处于 PoC 深挖与实现准备阶段”**。

---

## 2. PoC 总览表

| PoC 编号 | 主题                         | 目标                                                   | 当前状态       | 结论要点/发现                                           | 后续动作 |
|---------|------------------------------|--------------------------------------------------------|----------------|--------------------------------------------------------|----------|
| PoC #1  | Netty + 虚拟线程线程模型     | 验证 Netty EventLoop + Java 21 虚拟线程组合是否可行   | ✅ 基础验证完成 | EventLoop 仅做 IO，业务逻辑 offload 到虚拟线程可行；未深入背压 | 补充背压与拒绝策略 PoC |
| PoC #2  | 22 字节固定头的粘包/半包处理 | 验证 ByteToMessageDecoder 对固定头 + Body 的处理正确性 | ✅ 验证完成并自检修正 | 使用 cumulation + readerIndex 控制，半包时不读 Body，粘包时循环解帧 | 整合到 mini-rpc-transport |
| PoC #3  | Kryo 基础行为与并发问题      | 了解 Kryo 默认行为 & 暴露线程安全问题                | ✅ 问题已复现   | 静态单例 Kryo + 多线程（尤其 parallel stream）会出现越界/数据错乱 | 引入 Kryo Pool 或 ThreadLocal 方案 |
| PoC #4  | Kryo + 虚拟线程并发复现 PoC  | 在虚拟线程环境下 **100% 复现** Kryo 并发问题          | 🚧 设计进行中   | 已确认虚拟线程也会并发访问 Kryo，下一步是构造高并发、强冲突场景 | 补全 PoC 代码与说明 |
| PoC #5  | WSL vs Windows 性能对比      | 在同机对比 WSL / Windows 运行服务端+客户端的性能差异 | ✅ 初步对比完成 | 当前实验中 WSL 下同样并发量耗时明显更短（约缩短一倍量级）       | 后续用统一压测脚本重测 |

> 说明：编号并非严格时间顺序，而是逻辑分组；PoC #3 与 #4 属于同一主题（Kryo），#5 属于运行环境与性能验证。

---

## 3. 各 PoC 详细记录

### 3.1 PoC #1 —— Netty + 虚拟线程线程模型验证

**目的**
- 确认：在 Netty 基于 NIO 的 EventLoop 上，业务线程可以安全地切换为 Java 21 虚拟线程，避免“在 EventLoop 内阻塞”的风险。
- 验证：简单 Echo/RPC 场景下，`channelRead` → 提交虚拟线程执行 → 写回响应的链路是否稳定。

**当前进度**
- 已实现：基础 Echo/请求-响应 PoC，服务端在 handler 中将请求封装为任务提交到 `ExecutorService`（虚拟线程）、等待结果后写回。
- 已验证：
    - EventLoop 只负责 IO 与调度，不阻塞长耗时任务。
    - 虚拟线程数量可大于物理线程数，而不会直接拖垮 OS 线程。

**已识别问题/后续方向**
- 尚未 PoC：
    - 大量并发请求下，虚拟线程调度是否会导致内存压力。
    - 需要配合“请求队列 + 背压/拒绝策略”进行二次验证。

---

### 3.2 PoC #2 —— 22 字节固定头的粘包/半包处理

**目的**
- 针对 MiniRPC 协议：固定头 22B + 可扩展头 + Body，验证 Netty `ByteToMessageDecoder` 如何：
    - 正确识别半包（数据不足时不解码）；
    - 正确处理粘包（一次 ByteBuf 中包含多个完整帧）。

**关键点**
- 解码逻辑必须：
    1. **先看可读字节是否 ≥ 22**，否则直接 return 等待更多数据；
    2. 标记 readerIndex，解析固定头（Magic/Version/SerializeType/Flags/RequestId/HeaderLength/BodyLength）；
    3. 根据 `HeaderLength + BodyLength` 计算整帧长度，若数据不足，将 readerIndex 回退并 return；
    4. 数据充足时，读出一帧，构建内部对象（如 `RpcFrame`），加入 out 列表；
    5. 循环解帧，直到可读字节不足以组成下一帧。

**当前进度**
- 已完成：
    - 第一版 PoC 编码实现，并通过日志验证半包/粘包场景；
    - 后续进行“自检”，修正了：
        - 对可读长度判断不严谨的问题；
        - 部分分支未正确回退 readerIndex 的潜在 bug。

**下一步**
- 将 PoC 中的 Decoder 抽取为 `mini-rpc-transport` 模块内的正式解码器，结合协议文档中的字段定义（以 `PROTOCOL_Final.md` 为真源）。

---

### 3.3 PoC #3 —— Kryo 行为与并发问题曝光

**目的**
- 熟悉 Kryo 的使用方式、默认配置和错误模式。
- 显式复现“不安全用法”：例如 **静态单例 `Kryo` 对象被多线程共享** 时产生的数据错乱或异常。

**典型错误用法（已在 PoC 中使用）**

```java
private static final Kryo KRYO = new Kryo();
