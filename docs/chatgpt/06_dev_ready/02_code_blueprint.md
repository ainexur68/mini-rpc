# MiniRPC 1.0 Code Blueprint (Packages / Classes / Interfaces / Signatures)

> This document is **dev-ready**: class names, package paths, core interfaces, and minimal method signatures.
> JDK: **21**. Build: Maven multi-module.

---

## 1. Maven Modules & Dependencies

### 1.1 Modules
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

### 1.2 Dependency rules (must follow)
- `minirpc-protocol` -> `minirpc-common`
- `minirpc-serialization` -> `minirpc-common`
- `minirpc-transport-netty` -> `minirpc-common`, `minirpc-protocol`, `minirpc-serialization`
- `minirpc-registry-redis` -> `minirpc-common`
- `minirpc-loadbalancer` -> `minirpc-common`
- `minirpc-governance` -> `minirpc-common`, (and only API from core, avoid impl)
- `minirpc-core` -> all above (composition layer)

---

## 2. Common Layer (`minirpc-common`)

### 2.1 Package
`top.ainexur.minirpc.common`

### 2.2 Core Models
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

### 2.3 Error Model
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

## 3. Protocol Layer (`minirpc-protocol`)

### 3.1 Packages
- `top.ainexur.minirpc.protocol`
- `top.ainexur.minirpc.protocol.frame`
- `top.ainexur.minirpc.protocol.codec`

### 3.2 Fixed Header Constants
```java
package top.ainexur.minirpc.protocol;

public final class MiniRpcProtocol {
    public static final short MAGIC = (short) 0xCAFE;
    public static final byte VERSION_1 = 1;
    public static final int FIXED_HEADER_BYTES = 22;
    private MiniRpcProtocol() {}
}
```

### 3.3 Flags bits (16-bit)
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

### 3.4 Frame Model
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

### 3.5 Message Models (Body)
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

### 3.6 Frame Parse/Encode (Protocol) + Netty Frame Slicer
```java
package top.ainexur.minirpc.protocol.codec.frame;

import top.ainexur.minirpc.protocol.frame.MiniRpcFrame;

public final class FrameParser {
    public MiniRpcFrame parse(byte[] frameBytes) {
        // validate magic/version/length, then parse fields
    }
}

public final class FrameEncoder {
    public byte[] encode(MiniRpcFrame frame) {
        // encode fixed header + ext header + body
    }
}
```

```java
package top.ainexur.minirpc.transport.netty.codec;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.MessageToByteEncoder;
import java.util.List;
import top.ainexur.minirpc.protocol.codec.frame.FrameParser;
import top.ainexur.minirpc.protocol.codec.frame.FrameEncoder;
import top.ainexur.minirpc.protocol.frame.MiniRpcFrame;

public final class NettyFrameSlicer extends ByteToMessageDecoder {
    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
        // only slice full frame bytes, then use FrameParser
    }
}

public final class NettyFrameEncoder extends MessageToByteEncoder<MiniRpcFrame> {
    @Override
    protected void encode(ChannelHandlerContext ctx, MiniRpcFrame msg, ByteBuf out) {
        // use FrameEncoder to write bytes
    }
}
```

### 3.7 Frame <-> Message codec (body serialize/deserialize)
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

## 4. Serialization Layer (`minirpc-serialization`)

### 4.1 SPI Interface
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

### 4.3 JSON impl
`top.ainexur.minirpc.serialization.json.JsonSerializer` with `serializeType() == 0`

> Note: choose a JSON library (Jackson recommended). Keep it isolated in this module.

### 4.4 Post-1.0 (Kryo, optional)
Kryo is excluded from 1.0 delivery. Keep SPI extensible, but only ship JSON in 1.0.

---

## 5. Transport Layer (`minirpc-transport-netty`)

### 5.1 Packages
- `top.ainexur.minirpc.transport`
- `top.ainexur.minirpc.transport.netty`

### 5.2 Frozen interfaces (must exist)
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

### 5.3 Netty implementation contracts
- `NettyTransportServer`:
  - Starts `ServerBootstrap`
  - Pipeline must include `NettyFrameSlicer` + `MessageCodec` decode to `RpcRequest`
  - Dispatch to provider via `RequestHandler`
  - Encode `RpcResponse` → frame

- `NettyTransportClient`:
  - Keeps connection per endpoint (via `ConnectionManager`)
  - Uses inflight map: `requestId -> CompletableFuture<RpcResponse>`
  - On channel inactive: fail all inflight futures of that channel

### 5.4 Provider business offload (virtual thread)
```java
package top.ainexur.minirpc.transport;

import top.ainexur.minirpc.protocol.message.RpcRequest;
import top.ainexur.minirpc.protocol.message.RpcResponse;

public interface RequestHandler {
    RpcResponse handle(RpcRequest request);
}
```

Implementation detail:
- In Netty handler, do:
  - `Thread.startVirtualThread(() -> { RpcResponse r = handler.handle(req); ctx.writeAndFlush(r); })`
  - (or use an Executor created via `Executors.newVirtualThreadPerTaskExecutor()`)

---

## 6. Core Layer (`minirpc-core`)

### 6.1 Packages
- `top.ainexur.minirpc.core`
- `top.ainexur.minirpc.core.export`
- `top.ainexur.minirpc.core.proxy`
- `top.ainexur.minirpc.core.invoke`
- `top.ainexur.minirpc.core.filter`
- `top.ainexur.minirpc.core.context`

### 6.2 Core contracts
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

### 6.3 Dispatcher (Provider side)
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
                    RpcResponse resp = f.join(); // governance will wrap with timeout/retry later
                    if (resp.code() != 0) throw new RuntimeException("RPC error: " + resp.code() + " " + resp.message());
                    return resp.returnValue();
                }
        );
    }
}
```

---

## 7. Governance Layer (`minirpc-governance`)

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

### 7.2 Required Filters
- `TimeoutFilter` (client-side future timeout)
- `RetryFilter` (max 1)
- `TraceFilter`
- `LoggingFilter`

> Governance filters should depend only on the **interfaces** (Invoker/TransportClient abstraction), not Netty classes.

---

## 8. Registry Layer (`minirpc-registry-redis`)

### 8.1 Interfaces
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

### 8.2 Redis implementation notes
- Key schema (suggested):
  - Provider set: `minirpc:providers:{interface}:{version}:{group}`
  - Provider instance hash: `minirpc:provider:{interface}:{version}:{group}:{host}:{port}`
- TTL:
  - Apply TTL on instance hash; renewal task every `ttlSeconds/2`
- Pub/Sub channel:
  - `minirpc:providers:events:{interface}:{version}:{group}`

---

## 9. LoadBalancer Layer (`minirpc-loadbalancer`)

### 9.1 Interface + SPI
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

Implementations:
- `RandomLoadBalancer`
- `RoundRobinLoadBalancer` (per ServiceKey maintain AtomicInteger index)

---

## 10. Example Apps

### Provider
- export `HelloService`
- start server on port 18080
- register to Redis (when E3 enabled)

### Consumer
- build proxy via `ReferenceFactory`
- call `hello("mini")` and print result + traceId

---

## 11. Self-check (Accuracy guardrails)

- Protocol header fields & sizes match PROTOCOL (22B, magic/version/serializeType/flags/requestId/headerLen/bodyLen).
- Flags bits mapping includes heartbeat + response bit.
- TransportServer/Client/ConnectionManager are present as required frozen interfaces.
- Virtual threads are used **only** for business handling, not Netty IO thread.
- Registry/LB/Governance are designed as pluggable components (SPI-friendly).
