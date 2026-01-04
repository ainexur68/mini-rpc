# MiniRPC

MiniRPC 是一个基于 Java 21 + Netty 的轻量级、模块化 RPC 框架。它强调清晰的协议分帧、可插拔组件与最小可测核心，
便于各层独立演进与替换。

## 概览
- 自定义二进制协议（固定头 + 可扩展头 + Body）
- 基于 Netty 的传输链路与切帧
- 序列化/注册中心/负载均衡/治理链路可插拔
- 提供可运行的 Provider/Consumer 示例

## 状态
当前仓库处于 E1/Beta 阶段，进度与关键决策见 `docs/progress_log.md`。

## 快速开始
构建示例依赖：
```bash
mvn -pl minirpc-example-provider,minirpc-example-consumer -am package
```

在 IDE 中运行入口类：
- Provider：`top.ainexur.minirpc.example.provider.Main`（参数：port，默认 8080）
- Consumer：`top.ainexur.minirpc.example.consumer.Main`（参数：host、port）

先启动 Provider，再运行 Consumer 即可看到请求/响应闭环。

命令行方式（无需 exec 插件）：
```bash
mvn -pl minirpc-example-provider -am -DskipTests package dependency:copy-dependencies
java -cp "minirpc-example-provider/target/classes:minirpc-example-provider/target/dependency/*" \
  top.ainexur.minirpc.example.provider.Main
```

```bash
mvn -pl minirpc-example-consumer -am -DskipTests package dependency:copy-dependencies
java -cp "minirpc-example-consumer/target/classes:minirpc-example-consumer/target/dependency/*" \
  top.ainexur.minirpc.example.consumer.Main 127.0.0.1 8080
```

## 环境要求
- JDK 21
- Maven 3.8+

## 构建
```bash
mvn -DskipTests package
```

## 测试
```bash
mvn test
```

指定模块测试：
```bash
mvn -pl minirpc-protocol test
```

## 配置说明
- Provider 默认端口：8080
- Consumer 参数：`host` `port`（默认 `127.0.0.1 8080`）
- 示例不依赖注册中心，注册中心与负载均衡未接入演示链路。

## 协议固定头（22 字节）
- `magic`（2 字节）
- `version`（1 字节）
- `serializeType`（1 字节）
- `flags`（2 字节）
- `requestId`（8 字节）
- `headerLen`（4 字节）
- `bodyLen`（4 字节）

## 模块状态
| 模块 | 状态 | 说明 |
| --- | --- | --- |
| minirpc-protocol | 已实现 | 帧编码/解码与校验 |
| minirpc-serialization | 已实现 | JSON + SPI 注册 |
| minirpc-transport-netty | 已实现 | Netty 传输与切帧 |
| minirpc-core | 已实现 | 发布、分发与代理 |
| minirpc-registry-redis | 进行中 | 注册中心设计落地 |
| minirpc-loadbalancer | 进行中 | 策略占位 |
| minirpc-governance | 进行中 | Filter 链路钩子 |
| minirpc-example-* | 已实现 | 可运行示例 |
| minirpc-poc | 实验性 | 实验与记录 |

## 模块结构
- `minirpc-common`：公共工具与错误码
- `minirpc-protocol`：帧编码/解码与协议定义
- `minirpc-serialization`：序列化 SPI 与 JSON 实现
- `minirpc-transport-netty`：Netty 传输、切帧与消息编解码
- `minirpc-registry-redis`：Redis 注册中心（TTL + Pub/Sub 设计）
- `minirpc-loadbalancer`：负载均衡策略
- `minirpc-governance`：治理链路（超时/重试/Trace 钩子）
- `minirpc-core`：服务发布、调用与客户端代理
- `minirpc-example-provider`：Provider 示例
- `minirpc-example-consumer`：Consumer 示例
- `minirpc-poc`：PoC 实验

## 文档
- `docs/chatgpt/03架构设计/`（架构概览与详细设计）
- `docs/chatgpt/06_dev_ready_cn/`（代码蓝图与测试计划）

## 贡献
欢迎提交 Issue 与 PR。建议保持模块边界清晰，并在行为变更时补充测试。

## License
暂无开源许可，仅用于学习与内部使用。
