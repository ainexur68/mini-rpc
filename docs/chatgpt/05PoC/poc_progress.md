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

## 1.0 边界说明（按架构评审）
- 1.0 版本仅提供默认 JSON 序列化能力；Kryo 仅作为后续可扩展性的 PoC 验证结论，不进入 1.0 实现与交付范围。
- 传输层按协议文档落地（固定头 22B + 扩展头 + Body），解码流程基于 PoC #2 验证结果整合。
- 线程模型采用 Netty IO 线程收发 + 虚拟线程执行业务，背压与拒绝策略以 PoC #6 方案为基线。

---

## 2. PoC 总览表

| PoC 编号 | 主题                         | 目标                                                   | 代码位置                                                                                               | 当前状态       | 结论要点/发现                                           | 后续动作 |
|---------|------------------------------|--------------------------------------------------------|--------------------------------------------------------------------------------------------------------|----------------|--------------------------------------------------------|----------|
| PoC #1  | Netty + 虚拟线程线程模型     | 验证 Netty EventLoop + Java 21 虚拟线程组合可行性     | `src/test/java/poc1/VirtualThreadNettyServer.java`, `src/test/java/poc1/SimpleClient.java`            | ✅ 基础验证完成 | EventLoop 只做 IO，业务 offload 到虚拟线程可行；未深入背压 | 补充背压与拒绝策略 PoC |
| PoC #2  | 22 字节固定头的粘包/半包处理 | 验证 ByteToMessageDecoder 处理固定头+Body 的正确性     | `src/test/java/poc2/MiniRpcDecoder.java`, `src/test/java/poc2/DecoderPoCTest.java`                    | ✅ 验证完成并自检 | cumulation + readerIndex 控制，半包不读，粘包循环解帧   | 整合到 mini-rpc-transport |
| PoC #3  | Kryo 基础行为与并发问题      | 了解 Kryo 默认行为，暴露线程安全问题                   | `src/test/java/poc3/KryoBadPoC.java`, `src/test/java/poc3/KryoPoC.java`, `src/test/java/poc3/KryoSafeSupport.java`, `src/test/java/poc3/RpcRequest.java` | ✅ 问题已复现   | 静态单例 Kryo + 多线程/parallel stream 出现越界/数据错乱 | 引入 Kryo Pool 或 ThreadLocal 方案 |
| PoC #4  | Kryo + 虚拟线程并发复现 PoC  | 在虚拟线程环境 100% 复现 Kryo 并发问题                 | `src/test/java/poc4/KryoVirtualThreadBadPoC.java`, `src/test/java/poc3/RpcRequest.java`, `src/test/java/poc3/OtherRequest.java` | ✅ 复现中 | 并发访问 Kryo 可触发数据损坏与 JVM 崩溃                | 稳定复现场景与异常统计 |
| PoC #5  | WSL vs Windows 性能对比      | 同机对比 WSL / Windows 运行服务端+客户端的性能差异     | 暂无代码/脚本                                                                                          | ✅ 初步对比完成 | WSL 下同并发耗时明显更短（约缩短一数量级）               | 用统一压测脚本重测 |
| PoC #6  | 背压/长连接压测（生产形态） | 验证虚拟线程模式的业务背压，区分握手拒绝 vs 业务拒绝   | `src/test/java/poc6/VirtualThreadBackpressureServer.java`, `src/test/java/poc6/BackpressureLoadClient.java`, `src/test/java/poc6/BackpressureLoadClientShortConnect.java` | ✅ 实测完成    | 长连接复用+高 backlog 无握手拒绝；maxInFlight/bizMs 可控；50k/2000/500 约 0.7s | 抬升 maxInFlight/负载逼近上限 |

> 说明：编号按主题分组；PoC #3/#4 聚焦 Kryo，PoC #5 聚焦运行环境。

---

## 3. 各 PoC 详细记录

### 3.1 PoC #1 —— Netty + 虚拟线程线程模型验证
**目的**：确认 Netty NIO EventLoop 上业务切换到 Java 21 虚拟线程的可行性，避免在 IO 线程阻塞。  
**进度**：基础 Echo/请求-响应 PoC 完成，handler 将任务提交虚拟线程池，IO 线程不阻塞。  
**实测记录（本机）**：
- 启动 `poc1.VirtualThreadNettyServer` 后跑 `poc1.SimpleClient`（50k 请求），客户端输出 `Total cost: 2176 ms`。
**运行命令**：
- `java -cp target/test-classes:target/classes:<deps> poc1.VirtualThreadNettyServer`
- `java -cp target/test-classes:target/classes:<deps> poc1.SimpleClient`
- 服务端日志同时出现 `nioEventLoopGroup-*` 的 IO 线程与 `VirtualThread[#*]`，说明业务已从 IO 线程 offload 到虚拟线程。
**后续**：叠加请求队列/背压/拒绝策略，观察内存/线程。

### 3.2 PoC #2 —— 22 字节固定头的粘包/半包处理
**目的**：验证固定头 22B + Ext + Body 的解码正确性。  
**要点**：先判可读 ≥22；标记 readerIndex；按 headerLen+bodyLen 计算帧长，不足则回退；循环解帧。  
**状态**：验证并自检完成，修正了 readerIndex 回退等问题；待整合到 transport。  
**实测记录（本机）**：
- 运行 `poc2.DecoderPoCTest`：半包不输出；补齐后输出 1 帧；粘包输出 2 帧。
- 关键输出：`After half frame, out=[]`；`After full frame, out=[Frame{reqId=100, hLen=0, bLen=5}]`；`After sticky frames, out=[Frame{reqId=100, hLen=0, bLen=5}, Frame{reqId=200, hLen=2, bLen=20}]`
**运行命令**：
- `java -cp target/test-classes:target/classes:<deps> poc2.DecoderPoCTest`

### 3.3 PoC #3 —— Kryo 行为与并发问题曝光
**目的**：熟悉 Kryo 配置与错误模式，复现不安全用法。  
**发现**：静态单例 Kryo 在多线程/parallel stream 下会竞态，出现越界/数据错乱。  
**实测记录（本机）**：
- `poc3.KryoBadPoC`：快速失败，典型报错为 `Kryo.readObject` 链路 NPE（DefaultGenerics/FieldSerializer），验证并发不安全。
- `poc3.KryoPoC`：ThreadLocal Kryo 完成全量循环，无数据损坏（程序正常退出）。
**运行命令**：
- `java -cp target/test-classes:target/classes:<deps> poc3.KryoBadPoC`
- `java -cp target/test-classes:target/classes:<deps> poc3.KryoPoC`
**后续**：采用 ThreadLocal 或对象池，限制在可信环境使用 Kryo。

### 3.4 PoC #4 —— Kryo + 虚拟线程并发复现
**目的**：在虚拟线程场景 100% 复现 Kryo 并发问题。  
**进度**：已确认虚拟线程也会并发访问 Kryo，需构造高并发强冲突场景。  
**实测记录（本机）**：
- `-Dpoc.total=200`：`ok=191 corrupted=0 errors=9 costMs=85`，可正常结束但出现异常计数。
- `-Dpoc.total=2000`：`ok=1955 corrupted=0 errors=45 costMs=116`，样例异常 `RuntimeException: serialize error`。
- `-Dpoc.total=10000`：触发 JVM 致命错误 `SIGSEGV`（G1ParScanThreadState），生成 `hs_err_pid77749.log`。
**补充复现（实现补全后）**：
- 新增参数 `-Dpoc.maxConcurrency`（限制并发）与异常样本输出。
- `-Dpoc.total=2000 -Dpoc.maxConcurrency=200`：出现数据损坏（类型错位、字段乱序）后触发 JVM `SIGSEGV`，错误帧落在 Kryo `UnsafeField` 写路径；生成 `hs_err_pid73544.log`。
**运行命令**：
- `java -cp target/test-classes:target/classes:<deps> -Dpoc.total=2000 poc4.KryoVirtualThreadBadPoC`
- `java -cp target/test-classes:target/classes:<deps> -Dpoc.total=2000 -Dpoc.maxConcurrency=200 poc4.KryoVirtualThreadBadPoC`
**后续**：排查 SIGSEGV 原因（JVM/OS 资源或 VT 调度限制），补充异常类型统计与稳定复现场景。

### 3.5 PoC #5 —— WSL vs Windows 性能对比
**目的**：对比同机 WSL / Windows 运行时性能差异。  
**现状**：初步对比 WSL 下耗时显著更低。  
**实测记录（本机）**：当前仓库未发现 PoC #5 的可执行脚本/代码，无法复测。  
**后续**：补充统一压测脚本和场景后再重测。

### 3.6 PoC #6 —— 背压/长连接压测（生产形态）
**目的**
- 验证虚拟线程模式下的业务背压是否生效，区分握手拒绝（connection refused）与业务背压拒绝（BUSY）。
- 近似生产形态（长连接复用、较大在途、低业务耗时）逼近本机性能上限。

**关键实现**
- 服务端：`src/test/java/poc6/VirtualThreadBackpressureServer.java`
  - 参数：`-Dpoc.port`（默认 9100）、`-Dpoc.maxInFlight`（默认 1000，可调）、`-Dpoc.bizMs`（默认 10，可调）
  - 调优：`SO_BACKLOG=8192`、`SO_REUSEADDR=true`
  - 背压：`Semaphore(MAX_IN_FLIGHT)`，超限立即 BUSY；业务在虚拟线程中执行。
- 客户端：`src/test/java/poc6/BackpressureLoadClient.java`
  - 长连接复用，避免短连接风暴。
  - 参数：`total threads connections host port`（默认 50000/100/500/127.0.0.1/9100）。
**运行命令**：
- `java -cp target/test-classes:target/classes:<deps> -Dpoc.maxInFlight=1000 -Dpoc.bizMs=1 poc6.VirtualThreadBackpressureServer`
- `java -cp target/test-classes:target/classes:<deps> poc6.BackpressureLoadClient 50000 100 500 127.0.0.1 9100`

**实测记录（本机）**
- 服务端：`-Dpoc.maxInFlight=1000 -Dpoc.bizMs=1`
- 客户端三档（均无 Connection refused）：
  - RunA `total=10k, threads=800, conns=100` → cost≈242ms
  - RunB `total=20k, threads=1200, conns=200` → cost≈457ms
  - RunC `total=50k, threads=2000, conns=500` → cost≈703ms
**补充小规模验证（本机）**
- 服务端：`-Dpoc.maxInFlight=100 -Dpoc.bizMs=1`
- 客户端：`total=5000, threads=100, conns=100` → `cost=139ms`；服务端退出打印 `stat ok=100 rejected=0`

**结论**
- 短连接风暴会导致握手级拒绝，需长连接复用 + 提升 backlog 才能进入业务层背压。
- 在途上限（maxInFlight）与业务耗时（bizMs）可控，50k 请求高并发压测无握手拒绝，耗时约 0.7s。

**下一步**
- 抬升服务端参数：`-Dpoc.maxInFlight=2000/5000`、`-Dpoc.bizMs=1/0`。
- 客户端参数提升：`total=100k`、`threads=4000`、`connections=1000`。
- 观测 BUSY/耗时与 CPU/内存/GC，定位硬件极限。
