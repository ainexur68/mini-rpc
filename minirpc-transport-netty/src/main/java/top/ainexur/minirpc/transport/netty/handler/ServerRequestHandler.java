package top.ainexur.minirpc.transport.netty.handler;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import top.ainexur.minirpc.common.RpcErrorCode;
import top.ainexur.minirpc.protocol.message.RpcRequest;
import top.ainexur.minirpc.protocol.message.RpcResponse;
import top.ainexur.minirpc.transport.RequestHandler;

import java.util.Map;
import java.util.concurrent.ExecutorService;

/**
 * 服务端请求处理器，在业务线程中执行业务逻辑并回写响应。
 */
public class ServerRequestHandler extends SimpleChannelInboundHandler<Object> {
    private final RequestHandler handler;
    private final ExecutorService executor;

    /**
     * 构造服务端处理器。
     *
     * @param handler 业务处理器
     * @param executor 业务执行器（推荐虚拟线程）
     */
    public ServerRequestHandler(RequestHandler handler, ExecutorService executor) {
        this.handler = handler;
        this.executor = executor;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Object msg) {
        if (!(msg instanceof RpcRequest request)) {
            return;
        }
        executor.submit(() -> {
            RpcResponse response;
            try {
                response = handler.handle(request);
                if (response == null) {
                    response = new RpcResponse(request.requestId(), RpcErrorCode.SERVER_ERROR.code,
                            null, Map.of("error", "null response"));
                }
            } catch (Exception ex) {
                response = new RpcResponse(request.requestId(), RpcErrorCode.SERVER_ERROR.code,
                        null, Map.of("error", ex.getMessage() == null ? "server error" : ex.getMessage()));
            }
            ctx.channel().writeAndFlush(response);
        });
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        ctx.close();
    }
}
