package top.ainexur.minirpc.transport;

import top.ainexur.minirpc.protocol.message.RpcRequest;
import top.ainexur.minirpc.protocol.message.RpcResponse;

import java.util.concurrent.CompletableFuture;

/**
 * 客户端传输接口，负责向指定端点发送请求并异步返回响应。
 */
public interface TransportClient {
    /**
     * 发送请求到指定端点。
     *
     * @param endpoint 目标地址
     * @param request  请求对象
     * @return 响应的异步结果
     */
    CompletableFuture<RpcResponse> send(Endpoint endpoint, RpcRequest request);

    /**
     * 关闭客户端并释放资源。
     */
    void close();
}
