package top.ainexur.minirpc.core;

import top.ainexur.minirpc.protocol.message.RpcRequest;
import top.ainexur.minirpc.transport.Endpoint;

/**
 * 调用上下文，包含端点与请求信息。
 */
public record Invocation(Endpoint endpoint, RpcRequest request) {
}
