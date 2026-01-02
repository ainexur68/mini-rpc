package top.ainexur.minirpc.transport.netty.codec;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageDecoder;
import top.ainexur.minirpc.protocol.codec.MessageCodec;
import top.ainexur.minirpc.protocol.frame.MiniRpcFrame;

import java.util.List;

/**
 * 将 MiniRpcFrame 解码为业务消息（RpcRequest / RpcResponse）。
 */
public class NettyMessageDecoder extends MessageToMessageDecoder<MiniRpcFrame> {
    private final MessageCodec codec;

    /**
     * 构造解码器，将帧解码为请求或响应对象。
     *
     * @param codec 消息编解码器
     */
    public NettyMessageDecoder(MessageCodec codec) {
        this.codec = codec;
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, MiniRpcFrame msg, List<Object> out) {
        Object decoded = codec.decode(msg);
        if (decoded != null) {
            out.add(decoded);
        }
    }
}
