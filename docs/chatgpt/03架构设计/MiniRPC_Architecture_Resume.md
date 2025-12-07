# MiniRPC 项目架构（简历精炼版）

## 1. 项目介绍

MiniRPC 是一个 **自研 Dubbo Lite 风格的轻量级 RPC 框架**，基于 Java 21 + Netty 实现，包含完整的协议层、传输层、注册中心、负载均衡、治理体系。项目旨在深入理解 RPC 核心原理，并展示系统架构能力。

框架支持：

- 自定义二进制协议（固定头 + 扩展头 + Body）
- Java 21 虚拟线程执行业务逻辑
- Redis 注册中心（TTL + Pub/Sub）
- 可插拔负载均衡（Random / RoundRobin / SPI 扩展）
- Filter 治理链（超时 / 重试 / TraceId）
- Netty 客户端连接池 + 心跳机制

---

## 2. 核心能力亮点（面试官最关注）

### ● 自定义协议能力

自行设计协议格式（Magic、版本、消息类型、HeaderLength、BodyLength），支持序列化扩展与可变扩展头，并给出 Hex Dump 与半包判断流程。

### ● 网络通信与高性能架构

- 基于 Netty 实现 TCP 长连接  
- 客户端使用 CompletableFuture 做异步响应匹配  
- 服务端使用 VirtualThread 执行器，提高并发能力与可读性  
- 心跳与重连机制保证连接存活  

### ● 服务治理体系

采用 Filter 责任链模式实现：

- 调用链日志
- TraceId 生成与透传
- 超时控制
- 重试策略（幂等性判断）

### ● 注册中心设计

基于 Redis 实现轻量服务发现：

- Set 结构存储实例
- TTL + 心跳续期
- Pub/Sub 推送变化
- Consumer 本地缓存 + 被动更新

---

## 3. 架构设计概述

系统由六个模块组成：

```
mini-rpc-core
mini-rpc-protocol
mini-rpc-transport
mini-rpc-registry
mini-rpc-loadbalancer
mini-rpc-governance
```

调用链路：

```
Consumer Proxy → Filter → LoadBalancer → Netty Client → Protocol Encoder
  → TCP → Netty Server → Protocol Decoder → VirtualThread 执行业务 → 返回响应
```

---

## 4. 技术选型理由（简历亮点）

- **虚拟线程**：比传统线程池更简洁，避免阻塞 Netty IO 线程。
- **SPI 机制**：让负载均衡、序列化、过滤器等模块可插拔。
- **Redis 作为注册中心**：部署简单，高学习价值。
- **自定义协议**：相比 gRPC/HTTP2 更可控，面试展示价值更高。

---

## 5. 个人贡献与价值

你在项目中主导了以下核心部分的设计与实现：

- 协议格式设计与编解码实现  
- 传输层 Netty Client/Server + 虚拟线程执行模型  
- 注册中心 TTL 模型与本地缓存一致性  
- Filter 治理链（超时/重试/TraceId）  
- 负载均衡 SPI 扩展体系  
- 完整架构文档、时序图、流程图  

项目展示了你对 **网络通信、可扩展架构设计、Java 并发模型、分布式基础设施** 的深入理解。

---

## 6. 一句话总结（放简历里最有力）

**基于 Java 21 + Netty 从零实现的轻量级 RPC 框架，具备自定义协议、虚拟线程并发模型、Redis 注册中心、Filter 治理体系与可插拔负载均衡。完整实现 RPC 的核心架构与链路，是展示系统设计能力的高含金量项目。**
