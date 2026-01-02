package top.ainexur.minirpc.transport.netty;

import top.ainexur.minirpc.common.RpcErrorCode;
import top.ainexur.minirpc.common.RpcException;
import top.ainexur.minirpc.protocol.message.RpcResponse;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 客户端 in-flight 请求表，负责 requestId 与 future 的关联与回收。
 */
public final class RequestInFlight {
    private final ConcurrentHashMap<Long, CompletableFuture<RpcResponse>> inflight = new ConcurrentHashMap<>();

    /**
     * 注册请求的 future。
     *
     * @param requestId 请求ID
     * @param future    响应 future
     */
    public void register(long requestId, CompletableFuture<RpcResponse> future) {
        CompletableFuture<RpcResponse> prev = inflight.putIfAbsent(requestId, future);
        if (prev != null) {
            throw new RpcException(RpcErrorCode.BAD_REQUEST, "duplicate requestId=" + requestId);
        }
    }

    /**
     * 完成指定请求并返回响应。
     *
     * @param response 响应对象
     */
    public void complete(RpcResponse response) {
        CompletableFuture<RpcResponse> future = inflight.remove(response.requestId());
        if (future != null) {
            future.complete(response);
        }
    }

    /**
     * 将指定请求标记为失败。
     *
     * @param requestId 请求ID
     * @param code      错误码
     * @param message   错误信息
     * @param cause     异常原因
     */
    public void fail(long requestId, RpcErrorCode code, String message, Throwable cause) {
        CompletableFuture<RpcResponse> future = inflight.remove(requestId);
        if (future != null) {
            future.completeExceptionally(new RpcException(code, message, cause));
        }
    }

    /**
     * 失败所有未完成请求（如连接断开时）。
     *
     * @param code    错误码
     * @param message 错误信息
     */
    public void failAll(RpcErrorCode code, String message) {
        for (var entry : inflight.entrySet()) {
            if (inflight.remove(entry.getKey(), entry.getValue())) {
                entry.getValue().completeExceptionally(new RpcException(code, message));
            }
        }
    }
}
