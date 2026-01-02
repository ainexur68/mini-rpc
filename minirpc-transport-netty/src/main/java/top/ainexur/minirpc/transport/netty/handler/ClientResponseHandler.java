package top.ainexur.minirpc.transport.netty.handler;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import top.ainexur.minirpc.common.RpcErrorCode;
import top.ainexur.minirpc.protocol.message.RpcResponse;
import top.ainexur.minirpc.transport.netty.RequestInFlight;

/**
 * 客户端响应处理器，负责完成 in-flight 请求。
 */
public class ClientResponseHandler extends SimpleChannelInboundHandler<Object> {
    private final RequestInFlight inflight;

    /**
     * 构造响应处理器。
     *
     * @param inflight in-flight 请求表
     */
    public ClientResponseHandler(RequestInFlight inflight) {
        this.inflight = inflight;
    }

    /**
     * 处理响应并完成对应 future。
     *
     * @param ctx 上下文
     * @param msg 解码后的消息
     */
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Object msg) {
        if (msg instanceof RpcResponse response) {
            inflight.complete(response);
        }
    }

    /**
     * 通道失活时，失败所有未完成请求。
     *
     * @param ctx 上下文
     */
    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        inflight.failAll(RpcErrorCode.CONNECTION_CLOSED, "channel inactive");
    }

    /**
     * 异常时关闭通道并失败请求。
     *
     * @param ctx   上下文
     * @param cause 异常
     */
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        inflight.failAll(RpcErrorCode.CONNECTION_CLOSED, cause.getMessage());
        ctx.close();
    }
}
