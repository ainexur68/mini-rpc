# MiniRPC 1.0 需求冻结文档（Final Version）

> 文档状态：**需求已冻结**  
> 版本号：**1.0**  
> 冻结日期：2025-12-xx  
> 文档目标：明确 MiniRPC 1.0 要做什么、不做什么，为架构设计与开发阶段提供稳定基准。

---

## 1. 文档目的（Purpose）

本文件用于冻结 **MiniRPC 1.0** 的功能范围、协议规范范围、接口边界和交付物。

冻结后：

- 不得随意新增、删除、修改需求  
- 架构设计必须基于本文件  
- 开发、测试、验收均需严格对齐本文件  

---

## 2. 项目目标（Project Goals）

MiniRPC 的目标是构建一个：

- 可演示、可解释、工程化的轻量级 RPC 框架  
- 展示 RPC 核心机制（协议、序列化、传输、注册中心、负载均衡、治理）  
- 使用现代 Java 技术（Java 21 + 虚拟线程 + Netty）  
- 具备良好扩展能力（SPI、可扩展协议、插件化组件）  
- 能输出可衡量的性能指标（QPS、P99 延迟）  

MiniRPC 不是要替代 Dubbo/gRPC，而是作为“可读、可讲、可展示”的工程项目。

---

## 3. MiniRPC 1.0 功能冻结范围（MVP 必须实现）

以下功能为 **1.0 版本的硬性要求**，不得删减或延后。

---

### 3.1 Transport Layer（传输层）

#### 必做内容：

- 基于 Netty 的 TCP 长连接  
- 支持 Request/Response 消息模型（同步阻塞 Future 模式）  
- 简单连接池（Consumer 侧）  
- Provider 侧业务逻辑由 Java 21 虚拟线程执行  
- 粘包/半包处理（基于协议长度字段）  
- 基础心跳机制（可选，但结构需支持）  

#### 固定接口：

- `TransportServer`
- `TransportClient`
- `ConnectionManager`

---

### 3.2 Protocol Layer（协议层，最终冻结版本）

MiniRPC 采用 **固定头 + 可扩展头 + Body** 的二进制帧格式，确保未来可扩展 TLS、压缩、TraceId 等能力。

---

### 3.2.1 协议总体结构

```
+---------------------------+
| 固定头 Fixed Header       | 22 bytes
+---------------------------+
| 可扩展头 Ext Header       | HeaderLength bytes（可为 0）
+---------------------------+
| Body                      | BodyLength bytes
+---------------------------+
```

---

### 3.2.2 固定头字段定义（22 字节）

| 字段名           | 大小  | 类型    | 说明                     |
| ------------- | --- | ----- | ---------------------- |
| Magic         | 2B  | short | 魔数，固定为 0xCAFE          |
| Version       | 1B  | byte  | 协议版本，MiniRPC 1.0 固定为 1 |
| SerializeType | 1B  | byte  | 序列化方式（0=JSON，1=Kryo…）  |
| Flags         | 2B  | short | bit 位标志位（心跳/压缩/加密/响应等） |
| RequestId     | 8B  | long  | 请求唯一 ID                |
| HeaderLength  | 4B  | int   | Ext Header 长度，可为 0     |
| BodyLength    | 4B  | int   | Body 长度                |

---

### 3.2.3 SerializeType（多序列化支持）

| 值       | 类型   | 描述         |
| ------- | ---- | ---------- |
| 0       | JSON | 默认序列化，易调试  |
| 1       | Kryo | 高性能二进制，可选  |
| 2–127   | 保留   | 作为官方扩展     |
| 128–255 | 用户扩展 | SPI 自定义序列化 |

---

### 3.2.4 Flags 位图（2 字节）

```
bit0: 心跳帧
bit1: Body 已压缩
bit2: Body 已加密
bit3: 单向请求（无需响应）
bit4: 响应帧（1 = Response）
bit5-bit15: 保留
```

---

### 3.2.5 可扩展头（Ext Header）

- `HeaderLength = 0` → 本帧无扩展头  
- Ext Header 用于未来的：
  - TraceId / SpanId  
  - 路由信息（service-group、version）  
  - 加密协商数据  
  - 元数据传递  

---

### 3.2.6 Body 区域

- Flags.bit4 = 0 → Body 为 `RpcRequest`
- Flags.bit4 = 1 → Body 为 `RpcResponse`

---

### 3.2.7 解码要求（兼容性保证）

解码器必须：

1. 先读取 22 字节固定头  
2. 校验 Magic、Version  
3. 跳过 Ext Header（根据 HeaderLength）  
4. 再读取 BodyLength 字节的 Body  

---

### 3.3 Registry Layer（注册中心）

#### 必做：

- 使用 Redis 实现 Provider 注册  
- Consumer 启动时拉取 Provider 列表  
- Provider 下线（TTL 过期自动下线）  
- 通过 Redis Pub/Sub 监听节点变更  

---

### 3.4 LoadBalancer Layer（负载均衡）

#### 必做：

- Random  
- RoundRobin  

#### 要求：

- 以 SPI 实现为插件化结构  

---

### 3.5 Governance Layer（服务治理）

#### 必做：

- 超时控制  
- 简单重试（最多一次）  
- TraceId 注入  
- 调用链路日志  

---

### 3.6 Core Framework（RPC 核心）

#### 必做：

- JDK 动态代理  
- Provider 服务暴露  
- Consumer 服务引用  
- Filter 链机制  

---

### 3.7 Example（演示工程）

必须包含：

- `example-provider`  
- `example-consumer`  
- Demo 接口（如 HelloService）  

---

## 4. 不在本次范围（Out of Scope）

- TLS/SSL 加密  
- HTTP/2 / gRPC 兼容  
- Service Mesh  
- 分布式事务  
- 配置中心  
- UI Dashboard  
- OTEL/Jaeger 链路追踪  

---

## 5. 关键交付物（Deliverables）

### 源码交付：

- mini-rpc-core  
- mini-rpc-protocol  
- mini-rpc-transport  
- mini-rpc-registry  
- mini-rpc-loadbalancer  
- mini-rpc-governance  
- mini-rpc-example  

### 文档交付：

- PROTOCOL.md  
- 架构设计文档  
- 调用链路序列图  
- 压测报告  

---

## 6. 验收标准（Acceptance Criteria）

### 功能：

| 功能     | 验收标准            |
| ------ | --------------- |
| 请求/响应  | 多次调用正确          |
| 超时     | 能主动中断           |
| LB     | 多 Provider 分布均匀 |
| 注册中心   | 上下线实时同步         |
| 编解码    | 粘包/半包处理正确       |
| Filter | 能打印请求+响应日志      |

---

### 性能：

| 指标             | 目标      |
| -------------- | ------- |
| 单 Provider QPS | ≥ 5k    |
| P99 延迟         | ≤ 20 ms |
| 连续压测 10 分钟     | 无崩溃     |

---

## 7. 风险与限制（Risks）

- Redis 注册中心无强一致性  
- Kryo 存在反序列化安全隐患  
- 压缩/加密未在 1.0 实现  
- 重试可能导致幂等问题  

---

## 8. 冻结声明（Requirement Freeze Declaration）

自本文件确认之时起：

### **MiniRPC 1.0 所有需求正式冻结**

任何新增需求必须进入 1.1+ 版本，不得影响 1.0 交付。
