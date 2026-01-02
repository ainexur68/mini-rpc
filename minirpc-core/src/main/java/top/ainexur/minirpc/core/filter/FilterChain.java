package top.ainexur.minirpc.core.filter;

import top.ainexur.minirpc.core.Invocation;
import top.ainexur.minirpc.protocol.message.RpcResponse;

import java.util.concurrent.CompletableFuture;

/**
 * 过滤器链接口。
 */
public interface FilterChain {
    /**
     * 执行下一环节。
     *
     * @param invocation 调用上下文
     * @return 响应 future
     */
    CompletableFuture<RpcResponse> next(Invocation invocation);
}
