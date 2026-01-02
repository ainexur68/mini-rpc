package top.ainexur.minirpc.transport;

import top.ainexur.minirpc.protocol.message.RpcRequest;
import top.ainexur.minirpc.protocol.message.RpcResponse;

/**
 * 服务端请求处理器，将请求转换为响应。
 */
public interface RequestHandler {
    /**
     * 处理请求并返回响应。
     *
     * @param request 请求对象
     * @return 响应对象
     */
    RpcResponse handle(RpcRequest request);
}
