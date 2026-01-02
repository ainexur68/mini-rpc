package top.ainexur.minirpc.transport.netty.codec;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import top.ainexur.minirpc.protocol.codec.frame.FrameEncoder;
import top.ainexur.minirpc.protocol.frame.MiniRpcFrame;

public class NettyFrameEncoder extends MessageToByteEncoder<MiniRpcFrame> {
    private final FrameEncoder encoder = new FrameEncoder();

    @Override
    protected void encode(ChannelHandlerContext ctx, MiniRpcFrame frame, ByteBuf out) {
        byte[] bytes = encoder.encode(frame);
        out.writeBytes(bytes);
    }
}
