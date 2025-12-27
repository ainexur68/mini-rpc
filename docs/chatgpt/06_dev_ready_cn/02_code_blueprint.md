# MiniRPC 1.0 代码蓝图（包 / 类 / 接口 / 签名）

> 本文档为 **开发就绪**：包含类名、包路径、核心接口与最小方法签名。
> JDK: **21**。构建：Maven 多模块。

---

## 1. Maven 模块与依赖

### 1.1 模块
- `minirpc-common`
- `minirpc-protocol`
- `minirpc-serialization`
- `minirpc-transport-netty`
- `minirpc-registry-redis`
- `minirpc-loadbalancer`
- `minirpc-governance`
- `minirpc-core`
- `minirpc-example-provider`
- `minirpc-example-consumer`

### 1.2 依赖规则（必须遵循）
- `minirpc-protocol` -> `minirpc-common`, `minirpc-serialization`
- `minirpc-serialization` -> `minirpc-common`
- `minirpc-transport-netty` -> `minirpc-common`, `minirpc-protocol`, `minirpc-serialization`
- `minirpc-registry-redis` -> `minirpc-common`
- `minirpc-loadbalancer` -> `minirpc-common`
- `minirpc-governance` -> `minirpc-common`（只依赖 core API，避免实现）
- `minirpc-core` -> 上述全部（组合层）

---

## 2. Common 层（`minirpc-common`）

### 2.1 包
`top.ainexur.minirpc.common`

### 2.2 核心模型
```java
package top.ainexur.minirpc.common.model;

public record Endpoint(String host, int port) {}

public record ServiceKey(String interfaceName, String version, String group) {
    public static ServiceKey of(String interfaceName) {
        return new ServiceKey(interfaceName, "1.0", "default");
    }
}

public record ServiceInstance(ServiceKey key, Endpoint endpoint, long weight, java.util.Map<String, String> metadata) {}
```

### 2.3 错误模型
```java
package top.ainexur.minirpc.common.error;

public enum RpcErrorCode {
    OK(0),
    TIMEOUT(1),
    CONNECTION_CLOSED(2),
    BAD_REQUEST(3),
    SERVER_ERROR(4),
    DESERIALIZE_ERROR(5),
    SERIALIZE_ERROR(6),
    NO_PROVIDER(7);

    public final int code;
    RpcErrorCode(int code) { this.code = code; }
}

public final class RpcException extends RuntimeException {
    private final RpcErrorCode code;
    public RpcException(RpcErrorCode code, String message) { super(message); this.code = code; }
    public RpcException(RpcErrorCode code, String message, Throwable cause) { super(message, cause); this.code = code; }
    public RpcErrorCode code() { return code; }
}
```

---

## 3. Protocol 层（`minirpc-protocol`）

### 3.1 包
- `top.ainexur.minirpc.protocol`
- `top.ainexur.minirpc.protocol.frame`
- `top.ainexur.minirpc.protocol.codec`

### 3.2 固定头常量
```java
package top.ainexur.minirpc.protocol;

public final class MiniRpcProtocol {
    public static final short MAGIC = (short) 0xCAFE;
    public static final byte VERSION_1 = 1;
    public static final int FIXED_HEADER_BYTES = 22;
    private MiniRpcProtocol() {}
}
```

### 3.3 Flags bits（16-bit）
```java
package top.ainexur.minirpc.protocol;

public final class FlagBits {
    public static final short HEARTBEAT = 1 << 0;
    public static final short COMPRESSED = 1 << 1;
    public static final short ENCRYPTED = 1 << 2;
    public static final short ONE_WAY = 1 << 3;
    public static final short RESPONSE = 1 << 4;
    private FlagBits() {}
}
```

### 3.4 Frame 模型
```java
package top.ainexur.minirpc.protocol.frame;

public record MiniRpcFrame(
        short magic,
        byte version,
        byte serializeType,
        short flags,
        long requestId,
        int headerLength,
        int bodyLength,
        byte[] extHeader,   // nullable or empty
        byte[] bodyBytes    // nullable or empty
) {}
```

### 3.5 Message 模型（Body）
```java
package top.ainexur.minirpc.protocol.message;

import java.util.Map;

public record RpcRequest(
        long requestId,
        String interfaceName,
        String methodName,
        String[] paramTypeNames,
        Object[] args,
        Map<String, String> attachments
) {}

public record RpcResponse(
        long requestId,
        int code,                 // RpcErrorCode.code
        String message,           // optional
        Object returnValue,       // nullable
        Map<String, String> attachments
) {}
```

### 3.6 Netty 编解码器（签名）
```java
package top.ainexur.minirpc.protocol.codec;

import top.ainexur.minirpc.protocol.frame.MiniRpcFrame;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.MessageToByteEncoder;
import java.util.List;

public final class MiniRpcFrameDecoder extends ByteToMessageDecoder {
    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        // implement per PROTOCOL: read 22B -> validate -> skip ext -> read body
    }
}

public final class MiniRpcFrameEncoder extends MessageToByteEncoder<MiniRpcFrame> {
    @Override
    protected void encode(ChannelHandlerContext ctx, MiniRpcFrame msg, ByteBuf out) throws Exception {
        // implement fixed header + ext header + body
    }
}
```

### 3.7 Frame <-> Message codec（Body 编解码）
```java
package top.ainexur.minirpc.protocol.codec;

import top.ainexur.minirpc.protocol.frame.MiniRpcFrame;
import top.ainexur.minirpc.protocol.message.RpcRequest;
import top.ainexur.minirpc.protocol.message.RpcResponse;

public interface MessageCodec {
    MiniRpcFrame encodeRequest(RpcRequest request);
    MiniRpcFrame encodeResponse(RpcResponse response);
    Object decode(MiniRpcFrame frame); // returns RpcRequest or RpcResponse
}
```

---

## 4. Serialization 层（`minirpc-serialization`）

### 4.1 SPI 接口
```java
package top.ainexur.minirpc.serialization;

public interface Serializer {
    byte serializeType();
    byte[] serialize(Object obj);
    <T> T deserialize(byte[] data, Class<T> type);
}
```

### 4.2 Loader/Registry
```java
package top.ainexur.minirpc.serialization;

import java.util.Map;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;

public final class SerializerRegistry {
    private final Map<Byte, Serializer> byType = new ConcurrentHashMap<>();

    public SerializerRegistry() {
        ServiceLoader.load(Serializer.class).forEach(s -> byType.put(s.serializeType(), s));
    }

    public Serializer required(byte type) {
        Serializer s = byType.get(type);
        if (s == null) throw new IllegalArgumentException("No serializer for type=" + type);
        return s;
    }
}
```

### 4.3 JSON 实现
`top.ainexur.minirpc.serialization.json.JsonSerializer`，`serializeType() == 0`

> 注意：选择 JSON 库（建议 Jackson），并隔离在本模块。

### 4.4 Post-1.0（Kryo，可选）
Kryo 不进入 1.0 交付。保留 SPI 可扩展性，但 1.0 仅发布 JSON。

---

## 5. Transport 层（`minirpc-transport-netty`）

### 5.1 包
- `top.ainexur.minirpc.transport`
- `top.ainexur.minirpc.transport.netty`

### 5.2 冻结接口（必须存在）
```java
package top.ainexur.minirpc.transport;

import top.ainexur.minirpc.common.model.Endpoint;
import top.ainexur.minirpc.protocol.message.RpcRequest;
import top.ainexur.minirpc.protocol.message.RpcResponse;

import java.util.concurrent.CompletableFuture;

public interface TransportServer {
    void start(Endpoint bind);
    void stop();
}

public interface TransportClient {
    CompletableFuture<RpcResponse> send(Endpoint endpoint, RpcRequest request);
    void close();
}

public interface ConnectionManager {
    // minimal: acquire a ready channel/connection for an endpoint
    Object acquire(Endpoint endpoint); // implementation may return Channel
    void release(Endpoint endpoint, Object connection);
    void close();
}
```

### 5.3 Netty 实现约束
- `NettyTransportServer`:
  - 启动 `ServerBootstrap`
  - Pipeline 必须包含 `MiniRpcFrameDecoder` + `MessageCodec` 解码为 `RpcRequest`
  - 分发到 provider 的 `RequestHandler`
  - 编码 `RpcResponse` → frame

- `NettyTransportClient`:
  - 每个 endpoint 维持连接（通过 `ConnectionManager`）
  - in-flight 映射：`requestId -> CompletableFuture<RpcResponse>`
  - channel inactive 时：fail 该 channel 的 inflight futures

### 5.4 Provider 业务 offload（虚拟线程）
```java
package top.ainexur.minirpc.transport;

import top.ainexur.minirpc.protocol.message.RpcRequest;
import top.ainexur.minirpc.protocol.message.RpcResponse;

public interface RequestHandler {
    RpcResponse handle(RpcRequest request);
}
```

实现提示：
- 在 Netty handler 中执行：
  - `Thread.startVirtualThread(() -> { RpcResponse r = handler.handle(req); ctx.writeAndFlush(r); })`
  -（或使用 `Executors.newVirtualThreadPerTaskExecutor()`）

---

## 6. Core 层（`minirpc-core`）

### 6.1 包
- `top.ainexur.minirpc.core`
- `top.ainexur.minirpc.core.export`
- `top.ainexur.minirpc.core.proxy`
- `top.ainexur.minirpc.core.invoke`
- `top.ainexur.minirpc.core.filter`
- `top.ainexur.minirpc.core.context`

### 6.2 Core 合约
```java
package top.ainexur.minirpc.core;

import top.ainexur.minirpc.common.model.ServiceKey;
import top.ainexur.minirpc.protocol.message.RpcRequest;
import top.ainexur.minirpc.protocol.message.RpcResponse;

public interface Invoker {
    RpcResponse invoke(RpcRequest request);
}

public interface Exporter {
    void export(ServiceKey key, Object impl);
    Object lookup(ServiceKey key);
}
```

### 6.3 Dispatcher（Provider 侧）
```java
package top.ainexur.minirpc.core.export;

import top.ainexur.minirpc.common.error.RpcErrorCode;
import top.ainexur.minirpc.common.error.RpcException;
import top.ainexur.minirpc.common.model.ServiceKey;
import top.ainexur.minirpc.protocol.message.RpcRequest;
import top.ainexur.minirpc.protocol.message.RpcResponse;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class ServiceExporter implements top.ainexur.minirpc.core.Exporter {
    private final Map<ServiceKey, Object> services = new ConcurrentHashMap<>();
    @Override public void export(ServiceKey key, Object impl) { services.put(key, impl); }
    @Override public Object lookup(ServiceKey key) { return services.get(key); }
}
```

```java
package top.ainexur.minirpc.core.export;

import top.ainexur.minirpc.common.error.RpcErrorCode;
import top.ainexur.minirpc.common.model.ServiceKey;
import top.ainexur.minirpc.protocol.message.RpcRequest;
import top.ainexur.minirpc.protocol.message.RpcResponse;

import java.lang.reflect.Method;
import java.util.Arrays;

public final class ProviderDispatcher {
    private final ServiceExporter exporter;

    public ProviderDispatcher(ServiceExporter exporter) { this.exporter = exporter; }

    public RpcResponse dispatch(RpcRequest req) {
        try {
            Object impl = exporter.lookup(ServiceKey.of(req.interfaceName()));
            if (impl == null) {
                return new RpcResponse(req.requestId(), RpcErrorCode.BAD_REQUEST.code, "No service", null, req.attachments());
            }
            Class<?> clazz = impl.getClass();
            Class<?>[] paramTypes = Arrays.stream(req.paramTypeNames())
                    .map(this::loadClass)
                    .toArray(Class[]::new);
            Method m = clazz.getMethod(req.methodName(), paramTypes);
            Object r = m.invoke(impl, req.args());
            return new RpcResponse(req.requestId(), RpcErrorCode.OK.code, "OK", r, req.attachments());
        } catch (Throwable t) {
            return new RpcResponse(req.requestId(), RpcErrorCode.SERVER_ERROR.code, t.toString(), null, req.attachments());
        }
    }

    private Class<?> loadClass(String name) {
        try { return Class.forName(name); }
        catch (ClassNotFoundException e) { throw new IllegalArgumentException("Bad param type: " + name, e); }
    }
}
```

### 6.4 Consumer Proxy
```java
package top.ainexur.minirpc.core.proxy;

import top.ainexur.minirpc.common.model.Endpoint;
import top.ainexur.minirpc.protocol.message.RpcRequest;
import top.ainexur.minirpc.protocol.message.RpcResponse;
import top.ainexur.minirpc.transport.TransportClient;

import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;

public final class ReferenceFactory {
    private final TransportClient client;
    private final Endpoint endpoint;
    private final AtomicLong requestIdGen = new AtomicLong(0);

    public ReferenceFactory(TransportClient client, Endpoint endpoint) {
        this.client = client;
        this.endpoint = endpoint;
    }

    @SuppressWarnings("unchecked")
    public <T> T create(Class<T> iface) {
        return (T) Proxy.newProxyInstance(
                iface.getClassLoader(),
                new Class[]{iface},
                (proxy, method, args) -> {
                    long requestId = requestIdGen.incrementAndGet();
                    String[] typeNames = java.util.Arrays.stream(method.getParameterTypes()).map(Class::getName).toArray(String[]::new);
                    Map<String, String> atts = new java.util.HashMap<>();
                    atts.putIfAbsent("traceId", UUID.randomUUID().toString());
                    RpcRequest req = new RpcRequest(requestId, iface.getName(), method.getName(), typeNames, args, atts);
                    CompletableFuture<RpcResponse> f = client.send(endpoint, req);
                    RpcResponse resp = f.join(); // 治理层后续会做 timeout/retry 封装
                    if (resp.code() != 0) throw new RuntimeException("RPC error: " + resp.code() + " " + resp.message());
                    return resp.returnValue();
                }
        );
    }
}
```

---

## 7. Governance 层（`minirpc-governance`）

### 7.1 Filter SPI
```java
package top.ainexur.minirpc.governance;

import top.ainexur.minirpc.protocol.message.RpcRequest;
import top.ainexur.minirpc.protocol.message.RpcResponse;

public interface Filter {
    RpcResponse filter(RpcRequest request, FilterChain chain);
}

public interface FilterChain {
    RpcResponse next(RpcRequest request);
}
```

### 7.2 必需 Filter
- `TimeoutFilter`（客户端 future 超时）
- `RetryFilter`（最多 1 次）
- `TraceFilter`
- `LoggingFilter`

> 治理 Filter 只依赖 **接口**（Invoker/TransportClient 抽象），不要依赖 Netty 类。

---

## 8. Registry 层（`minirpc-registry-redis`）

### 8.1 接口
```java
package top.ainexur.minirpc.registry;

import top.ainexur.minirpc.common.model.ServiceInstance;
import top.ainexur.minirpc.common.model.ServiceKey;

import java.util.List;
import java.util.function.Consumer;

public interface Registry {
    void register(ServiceInstance instance, int ttlSeconds);
    void unregister(ServiceInstance instance);
    List<ServiceInstance> lookup(ServiceKey key);

    // watch changes; implementation uses Redis Pub/Sub
    AutoCloseable subscribe(ServiceKey key, Consumer<List<ServiceInstance>> onChange);
}
```

### 8.2 Redis 实现说明
- Key schema（建议）：
  - Provider set: `minirpc:providers:{interface}:{version}:{group}`
  - Provider instance hash: `minirpc:provider:{interface}:{version}:{group}:{host}:{port}`
- TTL：
  - 实例 hash 设置 TTL；续租任务每 `ttlSeconds/2`
- Pub/Sub 通道：
  - `minirpc:providers:events:{interface}:{version}:{group}`

---

## 9. LoadBalancer 层（`minirpc-loadbalancer`）

### 9.1 接口 + SPI
```java
package top.ainexur.minirpc.lb;

import top.ainexur.minirpc.common.model.ServiceInstance;
import top.ainexur.minirpc.common.model.ServiceKey;
import top.ainexur.minirpc.protocol.message.RpcRequest;

import java.util.List;

public interface LoadBalancer {
    String name();
    ServiceInstance select(ServiceKey key, List<ServiceInstance> instances, RpcRequest request);
}
```

实现：
- `RandomLoadBalancer`
- `RoundRobinLoadBalancer`（按 ServiceKey 维度维护 AtomicInteger 索引）

---

## 10. 示例应用

### Provider
- export `HelloService`
- 启动 server：端口 18080
- 启动 Redis 注册（E3 启用时）

### Consumer
- 通过 `ReferenceFactory` 构建代理
- 调用 `hello("mini")` 并打印结果 + traceId

---

## 11. 自检（准确性守护）

- 协议头字段与尺寸匹配 PROTOCOL（22B, magic/version/serializeType/flags/requestId/headerLen/bodyLen）。
- Flags bits 包含 heartbeat 与 response 位。
- TransportServer/Client/ConnectionManager 为冻结接口且必须存在。
- 虚拟线程仅用于业务处理，不可用于 Netty IO 线程。
- Registry/LB/Governance 均为可插拔设计（SPI 友好）。
