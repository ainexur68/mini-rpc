# MiniRPC Architecture Full Edition

## 1. 背景与动机

MiniRPC 的目标是作为一个 Dubbo Lite 风格的轻量级 RPC 学习与工程项目...

## 2. 设计哲学（Dubbo Lite 风格）

MiniRPC 的设计遵循“小而精、强扩展、易理解、不牺牲核心能力”的原则，其核心理念来自 Dubbo，但进行了极大简化以便学习与展示：

- **分层清晰**：核心抽象（Invoker/Filter）、协议层、传输层、注册中心、治理层。
- **组件可插拔**：基于 Java SPI，所有序列化、负载均衡、过滤器都可扩展。
- **自定义二进制协议**：不依赖 HTTP/2，直接走 TCP + Netty。
- **学习价值优先**：所有流程可读可调试，便于展示工程能力。

---

## 3. 总体架构

MiniRPC 整体架构由以下模块组成：

- mini-rpc-core
- mini-rpc-protocol
- mini-rpc-transport
- mini-rpc-registry
- mini-rpc-governance
- mini-rpc-loadbalancer
- mini-rpc-example

系统运行路径为：

1. Consumer 动态代理拦截方法调用
2. Filter 链执行治理逻辑（TraceId、超时、重试）
3. 根据服务名从注册中心获取 Provider 列表
4. LoadBalancer 选择一个 Provider 实例
5. 通过 Netty Client 从连接池获取 Channel
6. Protocol Encoder 编码请求 → TCP 传输
7. Netty Server 接收 → 解码 → 提交虚拟线程执行
8. 执行业务方法 → 返回响应
9. Consumer 侧 Future 完成 → 返回业务结果

## 4. 协议设计（Protocol）

MiniRPC 采用 **自定义二进制协议**，目标是：高效、可扩展、易解析、易调试。

---

### 4.1 固定头（Fixed Header，长度：22 字节）

固定头字段如下：

| 字段                | 长度  | 描述                        |
| ----------------- | --- | ------------------------- |
| Magic (0xDA,0xBB) | 2B  | 协议魔数，用于快速过滤非法数据           |
| Version           | 1B  | 协议版本号                     |
| SerializeType     | 1B  | 序列化方式（0=JDK、1=JSON、未来可扩展） |
| MessageType       | 1B  | 请求/响应/心跳                  |
| Flags             | 1B  | 压缩、加密、心跳位                 |
| RequestId         | 8B  | 请求 ID，用于匹配响应              |
| HeaderLength      | 4B  | 扩展头长度                     |
| BodyLength        | 4B  | Body 长度                   |

---

### 4.2 扩展头（Ext Header，可变长）

扩展头用于携带：

- TraceId  
- 路由键  
- 附加元数据  

协议中通过 HeaderLength 指示其长度。

示例 JSON 表示（内部仍是二进制序列化）：

```json
{
  "traceId": "a8f3c910",
  "routeKey": "user.hash"
}
```

---

### 4.3 Body（请求体/响应体）

Body 中放置：

- RpcRequest（含 serviceName、method、paramTypes、args）
- RpcResponse（含 code、message、data）

Body 序列化方式由 SerializeType 决定。

---

### 4.4 二进制帧示例（Hex Dump）

以下是一个请求帧示例（非真实数据）：

```
DA BB 01 00 00 10 00 00 00 2A 00 00 00 20 00 00 01 23
|Magic|V|S|T|F| RequestId |HdrLen|BodyLen|  Body...
```

---

### 4.5 半包/粘包处理逻辑（Netty ByteToMessageDecoder）

半包判断流程：

1. ByteBuf 可读长度 < 固定头长度 → 等待更多数据  

2. 读取 HeaderLength + BodyLength，总长度为：  
   
   ```
   frameSize = 22 + HeaderLength + BodyLength
   ```

3. ByteBuf 可读数据不足 frameSize → 半包，return  

4. 足够则读取完整帧 → 解码对象

示例伪代码（关键逻辑）：

```java
if (in.readableBytes() < FIXED_HEADER_LENGTH) {
    return; // 半包
}

short magic = in.readShort();
byte version = in.readByte();
...

int headerLen = in.readInt();
int bodyLen = in.readInt();

int frameSize = FIXED_HEADER_LENGTH + headerLen + bodyLen;

if (in.readableBytes() < frameSize) {
    in.resetReaderIndex(); // 回到读取前位置
    return; // 半包
}

ByteBuf frame = in.readBytes(frameSize);
out.add(decodeFrame(frame));
```

---

### 4.6 为什么不使用 HTTP/2 / gRPC？

| 项目   | MiniRPC   | gRPC          |
| ---- | --------- | ------------- |
| 协议结构 | 自定义二进制    | HTTP/2 流式     |
| 传输层  | TCP       | TCP（HTTP/2）   |
| 可控性  | 极强        | 较弱，受限于 HTTP/2 |
| 教育价值 | 高         | 复杂，不利于学习      |
| 目标定位 | 学习 + 轻量框架 | 工业级生产系统       |

MiniRPC 的目标是让你 **真正理解 RPC 架构的本质**，因此保留高自由度的协议设计。

---

## 5. 传输层设计（Transport：Netty + 虚拟线程）

MiniRPC 的传输层由 Netty 提供高性能 NIO 能力，并结合 Java 21 虚拟线程实现业务处理的极简与高并发。

---

### 5.1 Netty Server 架构

Server 端使用：

- bossGroup：负责 accept
- workerGroup：负责 read/write & pipeline

Pipeline 结构如下：

```
[ByteToMessageDecoder] → [MessageToByteEncoder] → [ServerHandler]
```

---

### 5.2 Netty Client 架构

客户端维护一个 **连接池（ChannelPool）**，每个 Provider 至少维持 1–2 条长连接。

Pipeline：

```
[ClientDecoder] → [ClientEncoder] → [ClientHandler]
```

---

### 5.3 请求匹配机制（RequestId → CompletableFuture）

每次发起请求时：

1. 生成 RequestId

2. 创建 CompletableFuture

3. 放入 inflightMap：  
   
   ```
   Map<Long, CompletableFuture<RpcResponse>>
   ```

4. 写出请求

5. 等待响应时 ClientHandler 根据 RequestId 匹配 future

伪代码：

```java
CompletableFuture<RpcResponse> future = new CompletableFuture<>();
inflight.put(requestId, future);
channel.writeAndFlush(frame);
return future;
```

---

### 5.4 虚拟线程执行模型（Java 21 Virtual Threads）

服务端业务执行全部采用：

```java
Executors.newVirtualThreadPerTaskExecutor()
```

优点：

- 大量并发请求不需要庞大线程池
- 每个请求都像同步代码一样写法自然
- 与 Netty IO 线程模型解耦，不阻塞 worker 线程

流程图：

```
Netty IO Thread → decode → 提交业务逻辑 → VirtualThread 执行方法 → encode → write
```

---

### 5.5 ChannelPool 设计（简易版）

- 使用队列存放可用 Channel
- borrowChannel 时如不可用则新建连接
- 连接失效时自动移除并重建

---

### 5.6 心跳机制

客户端定期发送心跳帧：

Flags 中的 Heartbeat 位 = 1  
Body 可为空

服务端收到后立即返回一条心跳响应。

若连续 N 次无响应 → 认为连接断开 → 触发重连。

---

## 6. 注册中心设计（Redis Registry）

MiniRPC 使用 Redis 提供轻量服务注册能力。

---

### 6.1 服务数据结构

推荐结构：

```
mini_rpc:service:{serviceName} = Set< "ip:port" >
```

Provider 启动时注册：

```redis
SADD mini_rpc:service:UserService "10.0.0.12:8080"
EXPIRE mini_rpc:service:UserService 15
```

---

### 6.2 TTL + 心跳续期

Provider 定期续期 EXPIRE：

- 避免脏节点长期存在
- 兼顾简单性与稳定性

Consumer 也可根据时间戳判断实例是否过期。

---

### 6.3 服务变更通知（Pub/Sub）

当 Provider 上下线时发布事件：

```
PUBLISH mini_rpc:notify UserService:UPDATE
```

Consumer 监听通知，根据需要重新拉取实例列表。

---

### 6.4 CAP 分析（面试重点）

Redis 注册中心属于：

- **CP** 倾向（数据一致性强）
- 可用性受 Redis 单点影响

MiniRPC 的选择理由：

- 轻量
- 易部署
- 足够支撑学习与演示

局限：

- 不如 Nacos/Consul 在多副本一致性上强
- 不具备服务权重、配置能力

---

## 7. 治理层设计（Filter + 超时 + 重试 + TraceId）

治理层全部基于 Filter 责任链实现。

---

### 7.1 Filter 责任链模型

Filter 链结构：

```
LogFilter → TraceFilter → TimeoutFilter → RetryFilter → Invoker
```

调用方向：

```
前置处理 → 执行调用 → 后置处理
```

示例代码：

```java
public interface Filter {
    Object invoke(Invocation inv, FilterChain chain) throws Exception;
}
```

---

### 7.2 超时控制

客户端 Future 设置超时：

```java
future.orTimeout(timeoutMs, TimeUnit.MILLISECONDS)
```

超时发生时：

- Future completeExceptionally
- 不影响 Netty 通道状态
- RetryFilter 可根据策略判断是否重试

---

### 7.3 重试机制（含幂等性）

- 默认支持 1 次重试
- 非幂等方法禁止自动重试（通过注解声明）

重试流程：

```
失败 → 判断是否幂等 → 重试 → 失败 → 返回异常
```

---

### 7.4 TraceId 生成与传播

- 每次请求生成 UUID 或 Snowflake 作为 traceId
- 放入 Ext Header
- 服务端打印 traceId，便于全链路跟踪

---

### 7.5 调用链日志设计

每次 RPC 输出：

- 请求方法
- 请求参数
- 耗时
- TraceId
- Provider 实例地址

格式类似：

```
[TRACE a83ff11] UserService.getUser cost=12ms provider=10.0.0.2:8080
```

---

## 8. 负载均衡设计（LoadBalancer with SPI）

负载均衡用于在多个 Provider 实例间分配请求，是 RPC 框架中决定性能与稳定性的关键环节。

MiniRPC 将负载均衡设计为 **可插拔 SPI 扩展点**，避免与核心逻辑耦合。

---

### 8.1 策略一：Random（随机）

特点：

- 简单
- 快速
- 在实例数量较多时接近均匀分布

实现示意：

```java
public class RandomLoadBalancer implements LoadBalancer {
    private final Random random = new Random();
    @Override
    public ServiceInstance select(List<ServiceInstance> instances, Invocation inv) {
        return instances.get(random.nextInt(instances.size()));
    }
}
```

---

### 8.2 策略二：RoundRobin（轮询）

适合 Provider 处理能力一致的场景。

实现要点：

- 使用 AtomicInteger 做计数
- 防止整数溢出

伪代码：

```java
int index = Math.abs(counter.getAndIncrement());
return instances.get(index % instances.size());
```

---

### 8.3 一致性哈希（ConsistentHash）的扩展预留

MiniRPC 目前不内置一致性哈希，但通过 SPI 可无缝扩展。

一致性哈希适合：

- 需要“相同请求落到相同 Provider”的场景  
- 如按用户 ID 进行会话策略

扩展方式：

```
META-INF/services/com.minirpc.LoadBalancer
```

文件内容：

```
consistentHash=com.example.ConsistentHashLoadBalancer
```

---

### 8.4 为什么负载均衡采用 SPI 插件化？

原因：

- 开放封闭原则（OCP）
- 防止负载均衡策略污染核心代码
- 与 Dubbo 的负载均衡体系保持一致的扩展性
- 便于面试官理解你的设计理念

---

## 9. 设计决策列表（Design Decisions）

这是 MiniRPC 架构文档的核心亮点，用于展示你在项目中做出的系统性思考。

---

### 9.1 为什么使用 Java 21 虚拟线程？

- 大幅减少线程池调优复杂度  
- 业务逻辑同步写法，易维护  
- 与 Netty IO 线程隔离，不会阻塞 workerGroup  
- 单机并发能力更高  
- 展示“现代 Java 开发能力”

---

### 9.2 为什么协议采用“固定头 + 扩展头 + Body”？

- 固定头便于快速判断是否为合法帧  
- 扩展头长度可变，便于未来扩展 TraceId、路由、压缩、加密  
- Body 可根据 SerializeType 动态决定序列化方式  
- 符合 Dubbo / Bolt 等 RPC 协议结构  
- 高可扩展性，是面试官看的关键点

---

### 9.3 为什么不使用 HTTP/2？

选择自定义协议而非 gRPC/HTTP2 的原因：

- 更易学习 RPC 本质  
- 开发者可以完全掌控协议格式  
- 性能更高（无额外 HTTP 语义开销）  
- 面试价值更大（可解释协议栈）

---

### 9.4 为什么注册中心选择 Redis？

主要原因：

- 本地环境易部署  
- 使用 TTL 实现服务健康管理非常简单  
- 通过 Pub/Sub 可实现即时更新  
- 足以支撑 RPC 学习项目的规模  

取舍：

- CP 模型  
- 高可用能力不如 Nacos / Consul  
- 但学习与展示价值更高

---

### 9.5 为什么治理层采用 Filter 责任链？

- 扩展性强  
- 可插拔  
- 顺序控制明确  
- 与 Dubbo 保持一致  
- 便于实现限流、熔断、超时、TraceId、日志等功能

---

### 9.6 为什么负载均衡放在 Client 端而不是 Server？

- 更符合微服务系统设计趋势  
- 避免 Provider 压力过高  
- Client 更了解调用上下文，可做智能路由  
- 与 Dubbo / gRPC 一致

---

### 9.7 为什么使用 CompletableFuture 而非手写 Callback？

- CompletableFuture 更符合现代 Java 风格  
- 支持超时控制、异常链路更清晰  
- 可与虚拟线程自然结合  
- 提升代码可读性与维护性

---

### 9.8 为什么暂不做 TLS / 鉴权？

MiniRPC 的定位是轻量 RPC 学习框架，因此：

- 优先让网络、协议、路由、传输变得清晰  
- 安全能力将在 2.x 版本加入（如 TLS、JWT）  
- 避免初期复杂度过高

---

## 10. MiniRPC vs Dubbo / vs gRPC

为了让面试官快速理解 MiniRPC 的定位，加入双对比表格。

---

### 10.1 MiniRPC vs Dubbo

| 维度       | MiniRPC | Dubbo     |
| -------- | ------- | --------- |
| 复杂度      | 低       | 高         |
| 协议       | 自定义     | Dubbo 协议  |
| 扩展性      | SPI     | SPI（更完善）  |
| 注册中心     | Redis   | Nacos/ZK  |
| 性能       | 高       | 很高        |
| 学习价值     | 极高      | 较高        |
| 集成度      | 低       | 高（治理体系完整） |
| 是否适合集群生产 | 否       | 是         |

---

### 10.2 MiniRPC vs gRPC

| 维度   | MiniRPC  | gRPC     |
| ---- | -------- | -------- |
| 传输协议 | TCP      | HTTP/2   |
| 序列化  | JDK/JSON | protobuf |
| 生态   | 轻        | 强        |
| 性能   | 高        | 很高       |
| 特性   | 可扩展协议    | 流式传输、双向流 |
| 场景   | 内网 RPC   | 跨语言场景    |

---

## 11. 未来路线图（Roadmap）

未来 MiniRPC 可演进方向：

### 11.1 协议层增强

- Gzip 压缩  
- TLS 加密  
- 多路复用（Multiplexing）  

### 11.2 注册中心增强

- 支持 Nacos  
- 支持服务权重  
- 支持 Provider 标签路由  

### 11.3 治理能力增强

- 熔断（Circuit Breaker）  
- 限流（RateLimit）  
- 调用链链路追踪（OpenTelemetry）  
- 指标采集（Metrics）  

### 11.4 Transport 增强

- 支持HTTP/2  
- 支持可选 KQueue/Epoll  

### 11.5 编程模型增强

- 增加注解驱动方式  
- 自动代理与服务扫描  
- SPI 插件市场  

---

## 12. 总结

MiniRPC 是一个结构清晰、可运行、可扩展的 Dubbo Lite 风格 RPC 框架。

它展示了：

- 自定义二进制协议  
- Netty 传输层实现  
- Java 21 虚拟线程的使用  
- Registry + LoadBalancer + Filter 的分层架构  
- 完整的 RPC 调用链路  
- 可复用的 SPI 扩展体系  

该项目非常适合作为：

- 简历亮点工程  
- 面试分享项目  
- 学习 RPC 核心机制的最佳示例  
