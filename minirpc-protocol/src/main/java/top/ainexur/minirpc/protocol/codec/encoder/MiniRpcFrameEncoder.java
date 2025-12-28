package top.ainexur.minirpc.protocol.codec.encoder;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import top.ainexur.minirpc.protocol.frame.MiniRpcFrame;

/**
 * MiniRpcFrameEncoder
 * <p>
 * 职责：
 * 1. 将 MiniRpcFrame 按协议顺序写入 ByteBuf
 * <p>
 * 明确不做：<p>
 * 1. 不校验 magic / version<p>
 * 2. 不关闭 Channel<p>
 * 3. 不修正 Frame 的不一致状态<p>
 */
public class MiniRpcFrameEncoder extends MessageToByteEncoder<MiniRpcFrame> {
    @Override
    protected void encode(ChannelHandlerContext ctx, MiniRpcFrame frame, ByteBuf byteBuf) {
        int headerLen = frame.headerLength();
        int bodyLen = frame.bodyLength();

        frame.validate();

        byteBuf.writeShort(MiniRpcFrame.MAGIC);
        byteBuf.writeByte(MiniRpcFrame.VERSION);
        byteBuf.writeByte(frame.serializeType());
        byteBuf.writeShort(frame.flags());
        byteBuf.writeLong(frame.requestId());
        byteBuf.writeInt(headerLen);
        byteBuf.writeInt(bodyLen);
        if (frame.headerLength() > 0) {
            byteBuf.writeBytes(frame.extHeader());
        }
        if (frame.bodyLength() > 0) {
            byteBuf.writeBytes(frame.body());
        }
    }
}
