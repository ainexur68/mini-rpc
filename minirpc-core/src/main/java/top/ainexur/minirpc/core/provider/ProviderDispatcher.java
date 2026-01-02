package top.ainexur.minirpc.core.provider;

import top.ainexur.minirpc.common.RpcErrorCode;
import top.ainexur.minirpc.protocol.message.RpcRequest;
import top.ainexur.minirpc.protocol.message.RpcResponse;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Objects;

/**
 * Provider 端分发器，负责方法反射调用。
 */
public class ProviderDispatcher {
    private final ServiceExporter exporter;

    /**
     * 构造分发器。
     *
     * @param exporter 服务导出器
     */
    public ProviderDispatcher(ServiceExporter exporter) {
        this.exporter = Objects.requireNonNull(exporter, "exporter");
    }

    /**
     * 分发请求并返回响应。
     *
     * @param request 请求
     * @return 响应
     */
    public RpcResponse dispatch(RpcRequest request) {
        try {
            Object impl = exporter.required(request.interfaceName());
            Class<?>[] paramTypes = resolveParamTypes(request.paramTypeNames());
            Method method = impl.getClass().getMethod(request.methodName(), paramTypes);
            Object result = method.invoke(impl, request.args());
            return new RpcResponse(request.requestId(), RpcErrorCode.OK.code, result, Map.of());
        } catch (Exception ex) {
            String message = ex.getMessage() == null ? "server error" : ex.getMessage();
            String detail = ex.getClass().getName() + ": " + message;
            return new RpcResponse(request.requestId(), RpcErrorCode.SERVER_ERROR.code, null, Map.of("error", detail));
        }
    }

    private static Class<?>[] resolveParamTypes(String[] typeNames) throws ClassNotFoundException {
        if (typeNames == null || typeNames.length == 0) {
            return new Class<?>[0];
        }
        Class<?>[] types = new Class<?>[typeNames.length];
        for (int i = 0; i < typeNames.length; i++) {
            types[i] = resolveType(typeNames[i]);
        }
        return types;
    }

    private static Class<?> resolveType(String name) throws ClassNotFoundException {
        return switch (name) {
            case "boolean" -> boolean.class;
            case "byte" -> byte.class;
            case "short" -> short.class;
            case "int" -> int.class;
            case "long" -> long.class;
            case "char" -> char.class;
            case "float" -> float.class;
            case "double" -> double.class;
            case "void" -> void.class;
            default -> Class.forName(name);
        };
    }
}
