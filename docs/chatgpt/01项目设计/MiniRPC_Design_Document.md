# MiniRPC 项目设计文档（Draft）

## 1. 项目背景（Background）

在实际 Java 后端开发中，RPC（Remote Procedure Call）是微服务内部高性能通信的核心技术。主流方案如 Dubbo、gRPC、Spring Cloud 生态较为成熟，但也较为复杂，不利于快速理解底层原理。

本项目 MiniRPC 的目标是：

- 构建一个**可演示、可解释、可扩展**的轻量级 RPC 框架
- 帮助开发者理解 RPC 底层的网络模型、序列化机制、协议封装、服务发现与治理原理
- 作为简历项目展示工程能力、系统设计能力与网络基础能力
- 采用 Java21（虚拟线程）+ Netty + 可插拔组件体系，符合现代 Java 技术趋势

MiniRPC 的定位不是取代成熟框架，而是成为一个“可读可学的工程级示例项目”。

---

## 2. 项目整体目标（Project Goals）

- 实现一个支持多节点、可扩展的 RPC 通信框架
- 包含自定义协议、序列化、传输层、服务发现、负载均衡、容错机制等
- 提供一个可维护、可扩展、具有明显工程结构的 Java21 项目
- 自带压测与验证工具，可展示性能指标
- 项目文档清晰，可作为简历亮点

---

## 3. 架构设计（Architecture Overview）

MiniRPC 遵循分层解耦思想，整体由五大模块组成：

### 3.1 Transport Layer（传输层）
负责网络通信：
- TCP 长连接管理
- 请求/响应模型（同步 + 异步）
- 编解码器
- Netty Reactor + Java21 Virtual Threads

### 3.2 Protocol Layer（协议层）
负责自定义 RPC 协议封帧：
- Magic Number、版本、序列化类型
- RequestId 保证异步关联
- BodyLength 解决粘包/半包问题

### 3.3 Registry Layer（注册中心）
提供服务发现能力：
- Provider 注册与心跳
- Consumer 订阅与监听
- 基于 Redis（快速版）或 Zookeeper（专业版）

### 3.4 Routing & LoadBalancer Layer（路由与负载均衡）
实现可插拔策略：
- Random
- Round-Robin
- Consistent Hash（适用于订单类业务）

### 3.5 Governance Layer（服务治理）
保障 RPC 稳定性：
- 超时控制
- 重试机制
- 熔断与自动恢复
- 限流（漏桶/滑动窗口算法）

---

## 4. 核心链路（Call Flow）

```
Consumer → 动态代理 → LoadBalancer → ConnectionPool → Encoder → Netty → Provider
          ← 返回结果 ← Decoder ← Netty ← Provider 执行 ← 业务方法
```

该链路覆盖：
- SPI 扩展体系
- 二进制协议解析
- IO 多路复用处理
- 虚拟线程执行任务
- 超时与重试控制

---

## 5. 技术选型（Tech Stack）

| 分类 | 技术 | 选择原因 |
|------|------|-----------|
| 编程语言 | Java 21 | 虚拟线程提升并发能力；语法现代 |
| 网络框架 | Netty | 稳定、成熟、与主流 RPC 一致 |
| 线程模型 | Reactor + Virtual Threads | IO 与业务分离，提升性能 |
| 序列化 | JSON、Kryo | 可调试 + 高性能 |
| 注册中心 | Redis Pub/Sub / ZK | 根据复杂度选择 |
| 架构模式 | DDD 分层 + SPI 扩展 | 工程可扩展能力强 |
| 日志 | SLF4J + Logback | 调用链追踪 |
| 压测 | JMH / wrk / 自写压测客户端 | 验证真实性能 |

---

## 6. 功能清单（Features）

### 6.1 MVP 功能
- 自定义 RPC 协议
- 单 Provider 远程调用成功
- 超时控制
- 本地文件/Redis 服务发现
- 简单日志追踪

### 6.2 完整功能
- 多 Provider 负载均衡
- Consistent Hash 路由
- 拦截器链（Filter）
- 熔断/重试/限流
- 连接池 + 心跳检测
- 调用链日志（TraceId）
- 多序列化支持
- 注册中心监听 & 自动上下线

---

## 7. 验证与测试（Testing & Benchmark）

### 7.1 功能测试
- 单元测试：编解码、注册中心、LB 策略
- 集成测试：Consumer → Provider 全链路验证
- 异常测试：节点宕机、网络异常、协议错误帧

### 7.2 性能测试（重要）
- 单节点吞吐量（QPS）
- 响应延迟分布（P95、P99）
- 虚拟线程 vs 普通线程对比
- 多节点场景下负载均衡效果

压测结果会成为你简历中的亮点内容。

---

## 8. 项目亮点（Resume Highlights）

- **从 0 到 1 设计 RPC 框架**：体现系统设计能力
- **掌握网络协议原理**：封帧、粘包、半包处理
- **现代 Java21 技术栈**：虚拟线程 + Netty 最佳实践
- **具备分布式系统基础能力**：注册中心、心跳管理、服务发现
- **工程能力出色**：SPI 扩展、模块化、拦截器体系
- **可观测性完整**：调用链与 Tracing

---

## 9. 项目结构（推荐）

```
mini-rpc/
 ├── mini-rpc-core
 ├── mini-rpc-transport
 ├── mini-rpc-protocol
 ├── mini-rpc-registry
 ├── mini-rpc-loadbalancer
 ├── mini-rpc-governance
 ├── mini-rpc-observability
 ├── mini-rpc-example
```

---

## 10. 后续可扩展方向

- 支持 HTTP/2 或 gRPC wire 协议
- 自研注册中心（基于 Raft/心跳）
- 支持 Tls / 加密通信
- OpenTelemetry 接入全链路追踪
- 提供 Dashboard 展示节点状态
