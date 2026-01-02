package top.ainexur.minirpc.protocol.codec.frame;

import top.ainexur.minirpc.protocol.MiniRpcProtocol;
import top.ainexur.minirpc.protocol.frame.MiniRpcFrame;

import java.nio.ByteBuffer;

public final class FrameEncoder {
    public byte[] encode(MiniRpcFrame frame) {
        int headerLen = frame.headerLength();
        int bodyLen = frame.bodyLength();

        frame.validate();

        ByteBuffer buffer = ByteBuffer.allocate(MiniRpcProtocol.FIXED_HEADER_SIZE + headerLen + bodyLen);
        buffer.putShort(MiniRpcFrame.MAGIC);
        buffer.put((byte) MiniRpcFrame.VERSION);
        buffer.put(frame.serializeType());
        buffer.putShort(frame.flags());
        buffer.putLong(frame.requestId());
        buffer.putInt(headerLen);
        buffer.putInt(bodyLen);
        if (headerLen > 0) {
            buffer.put(frame.extHeader());
        }
        if (bodyLen > 0) {
            buffer.put(frame.body());
        }
        return buffer.array();
    }
}
