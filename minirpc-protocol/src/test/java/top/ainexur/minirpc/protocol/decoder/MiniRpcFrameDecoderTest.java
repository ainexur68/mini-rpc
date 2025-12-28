package top.ainexur.minirpc.protocol.decoder;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.Test;
import top.ainexur.minirpc.common.RpcException;
import top.ainexur.minirpc.protocol.MiniRpcProtocol;
import top.ainexur.minirpc.protocol.codec.decoder.MiniRpcFrameDecoder;
import top.ainexur.minirpc.protocol.frame.MiniRpcFrame;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MiniRpcFrameDecoderTest {
    @Test
    void decodeHalfPacket() {
        // Verify half packet handling: first chunk yields no output, second completes one frame.
        byte[] extHeaders = "hdr".getBytes(StandardCharsets.UTF_8);
        byte[] body = "hello".getBytes(StandardCharsets.UTF_8);
        byte[] bytes = buildFrameBytes(MiniRpcProtocol.MAGIC, MiniRpcProtocol.VERSION, (byte) 1, (short) 0, 1L, extHeaders, body);
        int split = MiniRpcProtocol.FIXED_HEADER_SIZE + 1;

        EmbeddedChannel channel = new EmbeddedChannel(new MiniRpcFrameDecoder());
        try {
            channel.writeInbound(Unpooled.wrappedBuffer(bytes, 0, split));
            assertNull(channel.readInbound());

            channel.writeInbound(Unpooled.wrappedBuffer(bytes, split, bytes.length - split));
            MiniRpcFrame frame = channel.readInbound();
            assertNotNull(frame);
            assertEquals(MiniRpcProtocol.MAGIC, frame.MAGIC);
            assertEquals(MiniRpcProtocol.VERSION, frame.VERSION);
            assertArrayEquals(extHeaders, frame.extHeader());
            assertArrayEquals(body, frame.body());
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    @Test
    void decodeStickyPacket() {
        // Verify sticky packet handling: two frames in one buffer are decoded separately.
        byte[] extHeaders1 = "h1".getBytes(StandardCharsets.UTF_8);
        byte[] body1 = "a".getBytes(StandardCharsets.UTF_8);
        byte[] extHeaders2 = "h2".getBytes(StandardCharsets.UTF_8);
        byte[] body2 = "bb".getBytes(StandardCharsets.UTF_8);
        byte[] bytes1 = buildFrameBytes(MiniRpcProtocol.MAGIC, MiniRpcProtocol.VERSION, (byte) 1, (short) 0, 1L, extHeaders1, body1);
        byte[] bytes2 = buildFrameBytes(MiniRpcProtocol.MAGIC, MiniRpcProtocol.VERSION, (byte) 2, (short) 1, 2L, extHeaders2, body2);
        byte[] combined = concat(bytes1, bytes2);

        EmbeddedChannel channel = new EmbeddedChannel(new MiniRpcFrameDecoder());
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
        // Invalid magic should close the channel and surface a decoder exception.
        byte[] bytes = buildFrameBytes((short) 0x1234, MiniRpcProtocol.VERSION, (byte) 1, (short) 0, 1L, null, null);
        EmbeddedChannel channel = new EmbeddedChannel(new MiniRpcFrameDecoder());
        try {
            var ex = assertThrows(io.netty.handler.codec.DecoderException.class,
                    () -> channel.writeInbound(Unpooled.wrappedBuffer(bytes)));
            assertEquals(RpcException.class, ex.getCause().getClass());
            assertFalse(channel.isActive());
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    @Test
    void rejectBadVersion() {
        // Invalid version should close the channel and surface a decoder exception.
        byte[] bytes = buildFrameBytes(MiniRpcProtocol.MAGIC, (byte) 9, (byte) 1, (short) 0, 1L, null, null);
        EmbeddedChannel channel = new EmbeddedChannel(new MiniRpcFrameDecoder());
        try {
            var ex = assertThrows(io.netty.handler.codec.DecoderException.class,
                    () -> channel.writeInbound(Unpooled.wrappedBuffer(bytes)));
            assertEquals(RpcException.class, ex.getCause().getClass());
            assertFalse(channel.isActive());
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    @Test
    void rejectTooLargeFrame() {
        // Oversized frame should be rejected before reading any body bytes.
        int bodyLen = (int) MiniRpcProtocol.MAX_FRAME_SIZE + 1;
        ByteBuf buf = Unpooled.buffer(MiniRpcProtocol.FIXED_HEADER_SIZE);
        buf.writeShort(MiniRpcProtocol.MAGIC);
        buf.writeByte(MiniRpcProtocol.VERSION);
        buf.writeByte(1);
        buf.writeShort(0);
        buf.writeLong(1L);
        buf.writeInt(0);
        buf.writeInt(bodyLen);

        EmbeddedChannel channel = new EmbeddedChannel(new MiniRpcFrameDecoder());
        try {
            var ex = assertThrows(io.netty.handler.codec.DecoderException.class, () -> channel.writeInbound(buf));
            assertEquals(RpcException.class, ex.getCause().getClass());
            assertFalse(channel.isActive());
        } finally {
            channel.finishAndReleaseAll();
            if (buf.refCnt() > 0) {
                buf.release();
            }
        }
    }

    private static byte[] buildFrameBytes(short magic, byte version, byte serializeType, short flags, long requestId,
                                          byte[] extHeaders, byte[] body) {
        int headerLen = extHeaders == null ? 0 : extHeaders.length;
        int bodyLen = body == null ? 0 : body.length;
        ByteBuf buf = Unpooled.buffer(MiniRpcProtocol.FIXED_HEADER_SIZE + headerLen + bodyLen);
        buf.writeShort(magic);
        buf.writeByte(version);
        buf.writeByte(serializeType);
        buf.writeShort(flags);
        buf.writeLong(requestId);
        buf.writeInt(headerLen);
        buf.writeInt(bodyLen);
        if (headerLen > 0) {
            buf.writeBytes(extHeaders);
        }
        if (bodyLen > 0) {
            buf.writeBytes(body);
        }
        byte[] bytes = ByteBufUtil.getBytes(buf);
        buf.release();
        return bytes;
    }

    private static byte[] concat(byte[] left, byte[] right) {
        byte[] result = new byte[left.length + right.length];
        System.arraycopy(left, 0, result, 0, left.length);
        System.arraycopy(right, 0, result, left.length, right.length);
        return result;
    }
}
