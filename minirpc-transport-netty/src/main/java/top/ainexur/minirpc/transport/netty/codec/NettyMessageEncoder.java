package top.ainexur.minirpc.transport.netty.codec;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageEncoder;
import top.ainexur.minirpc.common.RpcErrorCode;
import top.ainexur.minirpc.common.RpcException;
import top.ainexur.minirpc.protocol.codec.MessageCodec;
import top.ainexur.minirpc.protocol.frame.MiniRpcFrame;
import top.ainexur.minirpc.protocol.message.RpcRequest;
import top.ainexur.minirpc.protocol.message.RpcResponse;

import java.util.List;

/**
 * 将请求或响应对象编码为 MiniRpcFrame。
 */
public class NettyMessageEncoder extends MessageToMessageEncoder<Object> {
    private final MessageCodec codec;

    /**
     * 构造编码器，将请求/响应对象编码为帧。
     *
     * @param codec 消息编解码器
     */
    public NettyMessageEncoder(MessageCodec codec) {
        this.codec = codec;
    }

    @Override
    protected void encode(ChannelHandlerContext ctx, Object msg, List<Object> out) {
        MiniRpcFrame frame;
        if (msg instanceof RpcRequest request) {
            frame = codec.encodeRequest(request);
        } else if (msg instanceof RpcResponse response) {
            frame = codec.encodeResponse(response);
        } else {
            throw new RpcException(RpcErrorCode.BAD_REQUEST, "unsupported outbound type: " + msg.getClass());
        }
        out.add(frame);
    }
}
