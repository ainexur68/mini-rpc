package top.ainexur.minirpc.core.filter;

import top.ainexur.minirpc.core.Invocation;
import top.ainexur.minirpc.protocol.message.RpcResponse;

import java.util.concurrent.CompletableFuture;

/**
 * 过滤器接口，用于在请求发送前后增强逻辑。
 */
public interface Filter {
    /**
     * 执行过滤逻辑。
     *
     * @param invocation 调用上下文
     * @param chain      过滤器链
     * @return 响应 future
     */
    CompletableFuture<RpcResponse> invoke(Invocation invocation, FilterChain chain);
}
