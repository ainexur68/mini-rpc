package top.ainexur.minirpc.core.consumer;

import top.ainexur.minirpc.common.RpcErrorCode;
import top.ainexur.minirpc.common.RpcException;
import top.ainexur.minirpc.core.Invocation;
import top.ainexur.minirpc.core.filter.DefaultFilterChain;
import top.ainexur.minirpc.core.filter.Filter;
import top.ainexur.minirpc.core.filter.FilterChain;
import top.ainexur.minirpc.protocol.message.RpcRequest;
import top.ainexur.minirpc.protocol.message.RpcResponse;
import top.ainexur.minirpc.transport.Endpoint;
import top.ainexur.minirpc.transport.TransportClient;

import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 引用工厂，基于 JDK 动态代理发起 RPC 调用。
 */
public class ReferenceFactory {
    private final TransportClient client;
    private final Endpoint endpoint;
    private final List<Filter> filters;
    private final AtomicLong requestId = new AtomicLong(1);

    /**
     * 构造引用工厂（无过滤器）。
     *
     * @param client   传输客户端
     * @param endpoint 目标端点
     */
    public ReferenceFactory(TransportClient client, Endpoint endpoint) {
        this(client, endpoint, Collections.emptyList());
    }

    /**
     * 构造引用工厂。
     *
     * @param client   传输客户端
     * @param endpoint 目标端点
     * @param filters  过滤器列表
     */
    public ReferenceFactory(TransportClient client, Endpoint endpoint, List<Filter> filters) {
        this.client = Objects.requireNonNull(client, "client");
        this.endpoint = Objects.requireNonNull(endpoint, "endpoint");
        this.filters = Objects.requireNonNull(filters, "filters");
    }

    /**
     * 创建接口的代理对象。
     *
     * @param iface 接口类型
     * @param <T>   类型
     * @return 代理对象
     */
    @SuppressWarnings("unchecked")
    public <T> T getProxy(Class<T> iface) {
        Objects.requireNonNull(iface, "iface");
        return (T) Proxy.newProxyInstance(
                iface.getClassLoader(),
                new Class<?>[]{iface},
                (proxy, method, args) -> {
                    if (method.getDeclaringClass() == Object.class) {
                        return method.invoke(this, args);
                    }
                    RpcRequest request = new RpcRequest(
                            requestId.getAndIncrement(),
                            iface.getName(),
                            method.getName(),
                            toTypeNames(method.getParameterTypes()),
                            args,
                            Map.of()
                    );
                    Invocation invocation = new Invocation(endpoint, request);
                    FilterChain terminal = (inv) -> client.send(inv.endpoint(), inv.request());
                    FilterChain chain = new DefaultFilterChain(filters, terminal);
                    CompletableFuture<RpcResponse> future = chain.next(invocation);
                    RpcResponse response = future.join();
                    if (response.code() != RpcErrorCode.OK.code) {
                        String message = "remote error";
                        if (response.attachments() != null) {
                            String detail = response.attachments().get("error");
                            if (detail != null && !detail.isBlank()) {
                                message = detail;
                            }
                        }
                        throw new RpcException(toErrorCode(response.code()), message);
                    }
                    return response.returnValue();
                }
        );
    }

    private static String[] toTypeNames(Class<?>[] types) {
        if (types == null || types.length == 0) {
            return new String[0];
        }
        String[] names = new String[types.length];
        for (int i = 0; i < types.length; i++) {
            names[i] = types[i].getName();
        }
        return names;
    }

    private static RpcErrorCode toErrorCode(int code) {
        for (RpcErrorCode value : RpcErrorCode.values()) {
            if (value.code == code) {
                return value;
            }
        }
        return RpcErrorCode.SERVER_ERROR;
    }
}
