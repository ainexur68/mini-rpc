# MiniRPC PoC 进度记录（poc_progress.md）

> 最近更新时间：2025-12-19  
> 说明：本文件用于在新 team 中快速恢复 MiniRPC 的 PoC 进度与上下文，作为架构设计与实现阶段之间的“缓冲层”。

---

## 1. 基线状态概览
- 设计文档：《MiniRPC_Design_Document.md》——已完成，作为项目背景与总体设计真源。
- 需求文档：《MiniRPC_1.0_Requirement_Freeze.md》——1.0 版本需求已冻结。
- 协议文档：《PROTOCOL_Final.md》——固定头 22B + 可扩展头 + Body 已定版。
- 架构文档：《MiniRPC_Architecture_Full.md》——分层架构、调用链路、模块边界已确定。
- 架构评审：《MiniRPC_Architecture_Review_Record_v1.md》——评审通过，修改已回写。

当前状态：**文档与架构基线已确定，处于 PoC 深挖与实现准备阶段。**

---

## 2. PoC 总览表

| PoC 编号 | 主题                         | 目标                                                   | 当前状态       | 结论要点/发现                                           | 后续动作 |
|---------|------------------------------|--------------------------------------------------------|----------------|--------------------------------------------------------|----------|
| PoC #1  | Netty + 虚拟线程线程模型     | 验证 Netty EventLoop + Java 21 虚拟线程组合可行性     | ✅ 基础验证完成 | EventLoop 只做 IO，业务 offload 到虚拟线程可行；未深入背压 | 补充背压与拒绝策略 PoC |
| PoC #2  | 22 字节固定头的粘包/半包处理 | 验证 ByteToMessageDecoder 处理固定头+Body 的正确性     | ✅ 验证完成并自检 | cumulation + readerIndex 控制，半包不读，粘包循环解帧   | 整合到 mini-rpc-transport |
| PoC #3  | Kryo 基础行为与并发问题      | 了解 Kryo 默认行为，暴露线程安全问题                   | ✅ 问题已复现   | 静态单例 Kryo + 多线程/parallel stream 出现越界/数据错乱 | 引入 Kryo Pool 或 ThreadLocal 方案 |
| PoC #4  | Kryo + 虚拟线程并发复现 PoC  | 在虚拟线程环境 100% 复现 Kryo 并发问题                 | 🚧 设计进行中   | 已确认虚拟线程也会并发访问 Kryo，需构造高并发强冲突场景 | 补全 PoC 代码与说明 |
| PoC #5  | WSL vs Windows 性能对比      | 同机对比 WSL / Windows 运行服务端+客户端的性能差异     | ✅ 初步对比完成 | WSL 下同并发耗时明显更短（约缩短一数量级）               | 用统一压测脚本重测 |
| PoC #6  | 背压/长连接压测（生产形态） | 验证虚拟线程模式的业务背压，区分握手拒绝 vs 业务拒绝   | ✅ 实测完成    | 长连接复用+高 backlog 无握手拒绝；maxInFlight/bizMs 可控；50k/2000/500 约 0.7s | 抬升 maxInFlight/负载逼近上限 |

> 说明：编号按主题分组；PoC #3/#4 聚焦 Kryo，PoC #5 聚焦运行环境。

---

## 3. 各 PoC 详细记录

### 3.1 PoC #1 —— Netty + 虚拟线程线程模型验证
**目的**：确认 Netty NIO EventLoop 上业务切换到 Java 21 虚拟线程的可行性，避免在 IO 线程阻塞。  
**进度**：基础 Echo/请求-响应 PoC 完成，handler 将任务提交虚拟线程池，IO 线程不阻塞。  
**后续**：叠加请求队列/背压/拒绝策略，观察内存/线程。

### 3.2 PoC #2 —— 22 字节固定头的粘包/半包处理
**目的**：验证固定头 22B + Ext + Body 的解码正确性。  
**要点**：先判可读 ≥22；标记 readerIndex；按 headerLen+bodyLen 计算帧长，不足则回退；循环解帧。  
**状态**：验证并自检完成，修正了 readerIndex 回退等问题；待整合到 transport。

### 3.3 PoC #3 —— Kryo 行为与并发问题曝光
**目的**：熟悉 Kryo 配置与错误模式，复现不安全用法。  
**发现**：静态单例 Kryo 在多线程/parallel stream 下会竞态，出现越界/数据错乱。  
**后续**：采用 ThreadLocal 或对象池，限制在可信环境使用 Kryo。

### 3.4 PoC #4 —— Kryo + 虚拟线程并发复现
**目的**：在虚拟线程场景 100% 复现 Kryo 并发问题。  
**进度**：已确认虚拟线程也会并发访问 Kryo，需构造高并发强冲突场景。  
**后续**：补充代码与说明，形成固定复现场景。

### 3.5 PoC #5 —— WSL vs Windows 性能对比
**目的**：对比同机 WSL / Windows 运行时性能差异。  
**现状**：初步对比 WSL 下耗时显著更低。  
**后续**：统一压测脚本和场景重测。

### 3.6 PoC #6 —— 背压/长连接压测（生产形态）
**目的**
- 验证虚拟线程模式下的业务背压是否生效，区分握手拒绝（connection refused）与业务背压拒绝（BUSY）。
- 近似生产形态（长连接复用、较大在途、低业务耗时）逼近本机性能上限。

**关键实现**
- 服务端：`src/test/java/poc4/VirtualThreadBackpressureServer.java`
  - 参数：`-Dpoc.port`（默认 9100）、`-Dpoc.maxInFlight`（默认 200，可调）、`-Dpoc.bizMs`（默认 10，可调）
  - 调优：`SO_BACKLOG=8192`、`SO_REUSEADDR=true`
  - 背压：`Semaphore(MAX_IN_FLIGHT)`，超限立即 BUSY；业务在虚拟线程中执行。
- 客户端：`src/test/java/poc4/BackpressureLoadClient.java`
  - 长连接复用，避免短连接风暴。
  - 参数：`total threads connections host port`（默认 50000/100/20/127.0.0.1/9100）。

**实测记录（本机）**
- 服务端：`-Dpoc.maxInFlight=1000 -Dpoc.bizMs=1`
- 客户端三档（均无 Connection refused）：
  - RunA `total=10k, threads=800, conns=100` → cost≈242ms
  - RunB `total=20k, threads=1200, conns=200` → cost≈457ms
  - RunC `total=50k, threads=2000, conns=500` → cost≈703ms

**结论**
- 短连接风暴会导致握手级拒绝，需长连接复用 + 提升 backlog 才能进入业务层背压。
- 在途上限（maxInFlight）与业务耗时（bizMs）可控，50k 请求高并发压测无握手拒绝，耗时约 0.7s。

**下一步**
- 抬升服务端参数：`-Dpoc.maxInFlight=2000/5000`、`-Dpoc.bizMs=1/0`。
- 客户端参数提升：`total=100k`、`threads=4000`、`connections=1000`。
- 观测 BUSY/耗时与 CPU/内存/GC，定位硬件极限。
