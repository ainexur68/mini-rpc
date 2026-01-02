package top.ainexur.minirpc.core.provider;

import top.ainexur.minirpc.common.RpcErrorCode;
import top.ainexur.minirpc.common.RpcException;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 服务导出器，负责接口名与实现对象的映射。
 */
public class ServiceExporter {
    private final ConcurrentHashMap<String, Object> services = new ConcurrentHashMap<>();

    /**
     * 注册服务实现。
     *
     * @param iface 接口类型
     * @param impl  实现对象
     */
    public void register(Class<?> iface, Object impl) {
        Objects.requireNonNull(iface, "iface");
        Objects.requireNonNull(impl, "impl");
        services.put(iface.getName(), impl);
    }

    /**
     * 获取服务实现，不存在则抛异常。
     *
     * @param interfaceName 接口名
     * @return 实现对象
     */
    public Object required(String interfaceName) {
        Object impl = services.get(interfaceName);
        if (impl == null) {
            throw new RpcException(RpcErrorCode.NO_PROVIDER, "no provider for " + interfaceName);
        }
        return impl;
    }
}
