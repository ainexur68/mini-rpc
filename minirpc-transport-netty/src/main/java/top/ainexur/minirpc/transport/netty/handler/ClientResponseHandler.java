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

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Object msg) {
        if (msg instanceof RpcResponse response) {
            inflight.complete(response);
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        inflight.failAll(RpcErrorCode.CONNECTION_CLOSED, "channel inactive");
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        inflight.failAll(RpcErrorCode.CONNECTION_CLOSED, cause.getMessage());
        ctx.close();
    }
}
