# mini-rpc

MiniRPC 是一个 **Dubbo Lite 风格的轻量级 RPC 框架**，基于 Java 21 + Netty 实现，模块化拆分便于单层理解与迭代。

## 背景
项目聚焦于实践 RPC 核心能力：自定义协议、序列化选择、服务发现与治理链路。通过 Maven 模块拆分，
让协议、传输、注册中心、负载均衡等能力可独立演进与替换。

## 目标
- 协议与帧结构保持简单明确
- 序列化、负载均衡、注册中心可插拔
- 提供可运行示例便于验证

## 核心能力
- 自定义二进制协议（固定头 + 可扩展头 + Body）
- 基于 Netty 的传输链路
- Redis 注册中心（TTL + Pub/Sub 设计）
- Filter 治理链（超时/重试/Trace）
- Provider/Consumer 示例模块

## 架构概览
Consumer → Filter → LoadBalancer → Netty Client → Protocol Encoder → TCP → Netty Server → Protocol Decoder → Invoker

## 模块列表
- minirpc-common
- minirpc-protocol
- minirpc-serialization
- minirpc-transport-netty
- minirpc-registry-redis
- minirpc-loadbalancer
- minirpc-governance
- minirpc-core
- minirpc-example-provider
- minirpc-example-consumer
- minirpc-poc

## 环境要求
- JDK 21
- Maven 3.8+

## 构建
```bash
mvn -q -DskipTests package
```

## 测试
```bash
mvn test
```

## 文档
- docs/chatgpt/03架构设计/（架构概览与详细设计）
- docs/chatgpt/06_dev_ready_cn/（代码蓝图与测试计划）

## Roadmap
- 完整请求/响应编解码链路
- 传输层集成测试
- 增强可观测性与治理能力

## 贡献
欢迎提交 Issue 与 PR，建议保持模块边界清晰并补充测试。

## License
待定
