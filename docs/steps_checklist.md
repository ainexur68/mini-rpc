# MiniRPC Steps Checklist

> 目标：记录每个阶段需要完成的事情，便于进度跟踪与验收。
> 说明：以 `docs/chatgpt/06_dev_ready_cn/01_epic_us_task.md` 为基线整理。

## E0 — 仓库 / 多模块骨架
- [x] T0.1 创建父项目（packaging `pom`，当前为 `mini-rpc`）
- [x] T0.2 创建基础模块（可编译骨架）
- [ ] T0.3 依赖方向约束（无“向上层依赖”）
- [ ] T0.4 surefire + failsafe 基线配置（全仓库）

## E1 — 垂直切片 MVP#1（Netty 请求/响应，无 Registry/LB）

### E1.1 协议分帧可处理粘包/半包
- [x] T1.1.1 固定头常量 + 布局
- [x] T1.1.2 实现 `MiniRpcFrame`
- [x] T1.1.3 Netty `ByteToMessageDecoder`（半包/粘包）
- [x] T1.1.4 Netty `MessageToByteEncoder`
- [x] UT-P1 半包解码
- [x] UT-P2 粘包解码
- [x] UT-P3 非法 magic 处理

### E1.2 JSON 序列化 SPI
- [x] T1.2.1 定义 `Serializer` SPI
- [x] T1.2.2 实现 `JsonSerializer`
- [x] T1.2.3 `SerializerRegistry` + `ServiceLoader`
- [x] UT-S1 request/response JSON 循环
- [x] UT-S2 ServiceLoader 可发现 JsonSerializer

### E1.3 Netty 传输（Client/Server）
- [x] T1.3.1 冻结接口：`TransportServer` / `TransportClient` / `ConnectionManager`
- [x] T1.3.2 实现 `NettyTransportServer`
- [x] T1.3.3 实现 `NettyTransportClient`
- [x] T1.3.4 实现 `SimpleConnectionManager`
- [x] IT-T1 server + client 请求/响应
- [x] IT-T2 并发 1k 请求完成
- [x] IT-T3 业务不在 Netty IO 线程（虚拟线程）

### E1.4 Core：Proxy + Provider Dispatch（本地内存注册）
- [ ] T1.4.1 `ServiceExporter`
- [ ] T1.4.2 `ProviderDispatcher`（反射调用）
- [ ] T1.4.3 `ReferenceFactory`（JDK 动态代理）
- [ ] T1.4.4 Filter chain 最小骨架
- [ ] IT-C1 完整链路 `HelloService#hello("x") -> "Hello x"`
- [ ] UT-C2 方法重载解析正确（若存在）

## E2 — 治理 MVP（超时 + 重试 + Trace + 日志）

### E2.1 超时控制
- [ ] 客户端超时（超时后 future 异常结束）
- [ ] 避免 transport 层重复超时
- [ ] UT-G1 超时触发

### E2.2 重试（最多 1 次）
- [ ] 可重试错误触发重试（timeout/connection closed）
- [ ] 记录重试次数用于日志
- [ ] UT-G2 第一次失败、第二次成功

### E2.3 TraceId + 调用日志
- [ ] `TraceFilter` 注入 traceId
- [ ] `LoggingFilter` 记录关键字段
- [ ] UT-G3 traceId 端到端存在

## E3 — Registry（Redis）+ LoadBalancer

### E3.1 Redis Registry
- [ ] `RedisRegistry` 注册 provider（TTL + 续租）
- [ ] consumer 拉取 provider 列表
- [ ] consumer 订阅 Pub/Sub 更新
- [ ] IT-R1 provider 注册与发现
- [ ] IT-R2 TTL 过期移除
- [ ] IT-R3 Pub/Sub 更新本地缓存

### E3.2 LoadBalancer via SPI
- [ ] 接口 `LoadBalancer#select(List<ServiceInstance>, Invocation)`
- [ ] 实现 `RandomLoadBalancer`
- [ ] 实现 `RoundRobinLoadBalancer`
- [ ] SPI 加载
- [ ] UT-L1 random 返回在列表内
- [ ] UT-L2 RR 按 serviceKey 轮询

## E4 — 心跳 + 连接鲁棒性

### E4.1 Heartbeat frame（bit0）
- [ ] client 空闲时周期性发送心跳
- [ ] server 立即响应
- [ ] 心跳 body 可为 0
- [ ] IT-H1 心跳往返

### E4.2 Connection recovery
- [ ] channel inactive 时 fail inflight futures
- [ ] 下次请求触发懒重连
- [ ] IT-H2 断线重连可恢复
