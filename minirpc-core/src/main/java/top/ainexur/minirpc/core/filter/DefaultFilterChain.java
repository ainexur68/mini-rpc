package top.ainexur.minirpc.core.filter;

import top.ainexur.minirpc.core.Invocation;
import top.ainexur.minirpc.protocol.message.RpcResponse;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 默认过滤器链实现，按顺序执行过滤器。
 */
public class DefaultFilterChain implements FilterChain {
    private final List<Filter> filters;
    private final FilterChain terminal;

    /**
     * 构造过滤器链。
     *
     * @param filters  过滤器列表
     * @param terminal 末端执行器
     */
    public DefaultFilterChain(List<Filter> filters, FilterChain terminal) {
        this.filters = Objects.requireNonNull(filters, "filters");
        this.terminal = Objects.requireNonNull(terminal, "terminal");
    }

    @Override
    public CompletableFuture<RpcResponse> next(Invocation invocation) {
        AtomicInteger index = new AtomicInteger(0);
        return invokeAt(invocation, index);
    }

    private CompletableFuture<RpcResponse> invokeAt(Invocation invocation, AtomicInteger index) {
        int i = index.getAndIncrement();
        if (i >= filters.size()) {
            return terminal.next(invocation);
        }
        Filter filter = filters.get(i);
        return filter.invoke(invocation, (nextInvocation) -> invokeAt(nextInvocation, index));
    }
}
