package top.ainexur.minirpc.transport.netty.codec;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import top.ainexur.minirpc.protocol.codec.frame.FrameEncoder;
import top.ainexur.minirpc.protocol.frame.MiniRpcFrame;

/**
 * Netty 编码器，将 MiniRpcFrame 编码为字节流。
 */
public class NettyFrameEncoder extends MessageToByteEncoder<MiniRpcFrame> {
    private final FrameEncoder encoder = new FrameEncoder();

    /**
     * 编码输出协议帧字节。
     *
     * @param ctx   上下文
     * @param frame 协议帧
     * @param out   输出缓冲区
     */
    @Override
    protected void encode(ChannelHandlerContext ctx, MiniRpcFrame frame, ByteBuf out) {
        byte[] bytes = encoder.encode(frame);
        out.writeBytes(bytes);
    }
}
