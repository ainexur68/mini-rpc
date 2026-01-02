package top.ainexur.minirpc.transport.netty.codec;

import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.Test;
import top.ainexur.minirpc.common.RpcException;
import top.ainexur.minirpc.protocol.MiniRpcProtocol;
import top.ainexur.minirpc.protocol.frame.MiniRpcFrame;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NettyFrameSlicerTest {
    @Test
    void decodeHalfPacket() {
        byte[] extHeaders = "hdr".getBytes(StandardCharsets.UTF_8);
        byte[] body = "hello".getBytes(StandardCharsets.UTF_8);
        byte[] bytes = buildFrameBytes(MiniRpcProtocol.MAGIC, MiniRpcProtocol.VERSION, (byte) 1, (short) 0, 1L, extHeaders, body);
        int split = MiniRpcProtocol.FIXED_HEADER_SIZE + 1;

        EmbeddedChannel channel = new EmbeddedChannel(new NettyFrameSlicer());
        try {
            channel.writeInbound(Unpooled.wrappedBuffer(bytes, 0, split));
            assertNull(channel.readInbound());

            channel.writeInbound(Unpooled.wrappedBuffer(bytes, split, bytes.length - split));
            MiniRpcFrame frame = channel.readInbound();
            assertNotNull(frame);
            assertEquals(1L, frame.requestId());
            assertArrayEquals(extHeaders, frame.extHeader());
            assertArrayEquals(body, frame.body());
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    @Test
    void decodeStickyPacket() {
        byte[] extHeaders1 = "h1".getBytes(StandardCharsets.UTF_8);
        byte[] body1 = "a".getBytes(StandardCharsets.UTF_8);
        byte[] extHeaders2 = "h2".getBytes(StandardCharsets.UTF_8);
        byte[] body2 = "bb".getBytes(StandardCharsets.UTF_8);
        byte[] bytes1 = buildFrameBytes(MiniRpcProtocol.MAGIC, MiniRpcProtocol.VERSION, (byte) 1, (short) 0, 1L, extHeaders1, body1);
        byte[] bytes2 = buildFrameBytes(MiniRpcProtocol.MAGIC, MiniRpcProtocol.VERSION, (byte) 2, (short) 1, 2L, extHeaders2, body2);
        byte[] combined = concat(bytes1, bytes2);

        EmbeddedChannel channel = new EmbeddedChannel(new NettyFrameSlicer());
        try {
            channel.writeInbound(Unpooled.wrappedBuffer(combined));
            MiniRpcFrame frame1 = channel.readInbound();
            MiniRpcFrame frame2 = channel.readInbound();
            assertNotNull(frame1);
            assertNotNull(frame2);
            assertEquals(1L, frame1.requestId());
            assertEquals(2L, frame2.requestId());
            assertArrayEquals(extHeaders1, frame1.extHeader());
            assertArrayEquals(body2, frame2.body());
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    @Test
    void rejectBadMagic() {
        byte[] bytes = buildFrameBytes((short) 0x1234, MiniRpcProtocol.VERSION, (byte) 1, (short) 0, 1L, null, null);
        EmbeddedChannel channel = new EmbeddedChannel(new NettyFrameSlicer());
        try {
            var ex = assertThrows(io.netty.handler.codec.DecoderException.class,
                    () -> channel.writeInbound(Unpooled.wrappedBuffer(bytes)));
            assertEquals(RpcException.class, ex.getCause().getClass());
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    private static byte[] buildFrameBytes(short magic, byte version, byte serializeType, short flags, long requestId,
                                          byte[] extHeaders, byte[] body) {
        int headerLen = extHeaders == null ? 0 : extHeaders.length;
        int bodyLen = body == null ? 0 : body.length;
        byte[] bytes = new byte[MiniRpcProtocol.FIXED_HEADER_SIZE + headerLen + bodyLen];
        int offset = 0;
        writeShort(bytes, offset, magic);
        bytes[offset + 2] = version;
        bytes[offset + 3] = serializeType;
        writeShort(bytes, offset + 4, flags);
        writeLong(bytes, offset + 6, requestId);
        writeInt(bytes, offset + 14, headerLen);
        writeInt(bytes, offset + 18, bodyLen);
        if (headerLen > 0) {
            System.arraycopy(extHeaders, 0, bytes, MiniRpcProtocol.FIXED_HEADER_SIZE, headerLen);
        }
        if (bodyLen > 0) {
            System.arraycopy(body, 0, bytes, MiniRpcProtocol.FIXED_HEADER_SIZE + headerLen, bodyLen);
        }
        return bytes;
    }

    private static byte[] concat(byte[] left, byte[] right) {
        byte[] result = new byte[left.length + right.length];
        System.arraycopy(left, 0, result, 0, left.length);
        System.arraycopy(right, 0, result, left.length, right.length);
        return result;
    }

    private static void writeShort(byte[] bytes, int offset, short value) {
        bytes[offset] = (byte) ((value >>> 8) & 0xff);
        bytes[offset + 1] = (byte) (value & 0xff);
    }

    private static void writeInt(byte[] bytes, int offset, int value) {
        bytes[offset] = (byte) ((value >>> 24) & 0xff);
        bytes[offset + 1] = (byte) ((value >>> 16) & 0xff);
        bytes[offset + 2] = (byte) ((value >>> 8) & 0xff);
        bytes[offset + 3] = (byte) (value & 0xff);
    }

    private static void writeLong(byte[] bytes, int offset, long value) {
        bytes[offset] = (byte) ((value >>> 56) & 0xff);
        bytes[offset + 1] = (byte) ((value >>> 48) & 0xff);
        bytes[offset + 2] = (byte) ((value >>> 40) & 0xff);
        bytes[offset + 3] = (byte) ((value >>> 32) & 0xff);
        bytes[offset + 4] = (byte) ((value >>> 24) & 0xff);
        bytes[offset + 5] = (byte) ((value >>> 16) & 0xff);
        bytes[offset + 6] = (byte) ((value >>> 8) & 0xff);
        bytes[offset + 7] = (byte) (value & 0xff);
    }
}
