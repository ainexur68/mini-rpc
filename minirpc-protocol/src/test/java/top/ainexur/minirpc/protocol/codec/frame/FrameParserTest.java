package top.ainexur.minirpc.protocol.codec.frame;

import org.junit.jupiter.api.Test;
import top.ainexur.minirpc.common.RpcException;
import top.ainexur.minirpc.protocol.MiniRpcProtocol;
import top.ainexur.minirpc.protocol.frame.MiniRpcFrame;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FrameParserTest {
    private final FrameParser parser = new FrameParser();

    @Test
    void parseValidFrame() {
        byte[] extHeaders = "hdr".getBytes(StandardCharsets.UTF_8);
        byte[] body = "hello".getBytes(StandardCharsets.UTF_8);
        byte[] bytes = buildFrameBytes(MiniRpcProtocol.MAGIC, MiniRpcProtocol.VERSION, (byte) 1, (short) 0, 1L, extHeaders, body);

        MiniRpcFrame frame = parser.parse(bytes);

        assertEquals(1L, frame.requestId());
        assertArrayEquals(extHeaders, frame.extHeader());
        assertArrayEquals(body, frame.body());
    }

    @Test
    void rejectBadMagic() {
        byte[] bytes = buildFrameBytes((short) 0x1234, MiniRpcProtocol.VERSION, (byte) 1, (short) 0, 1L, null, null);
        assertThrows(RpcException.class, () -> parser.parse(bytes));
    }

    @Test
    void rejectBadVersion() {
        byte[] bytes = buildFrameBytes(MiniRpcProtocol.MAGIC, (byte) 9, (byte) 1, (short) 0, 1L, null, null);
        assertThrows(RpcException.class, () -> parser.parse(bytes));
    }

    @Test
    void rejectTooLargeFrame() {
        int bodyLen = (int) MiniRpcProtocol.MAX_FRAME_SIZE + 1;
        byte[] bytes = buildFrameBytes(MiniRpcProtocol.MAGIC, MiniRpcProtocol.VERSION, (byte) 1, (short) 0, 1L, null, new byte[bodyLen]);
        assertThrows(RpcException.class, () -> parser.parse(bytes));
    }

    @Test
    void rejectLengthMismatch() {
        byte[] body = "hello".getBytes(StandardCharsets.UTF_8);
        byte[] bytes = buildFrameBytes(MiniRpcProtocol.MAGIC, MiniRpcProtocol.VERSION, (byte) 1, (short) 0, 1L, null, body);
        byte[] truncated = new byte[bytes.length - 1];
        System.arraycopy(bytes, 0, truncated, 0, truncated.length);
        assertThrows(RpcException.class, () -> parser.parse(truncated));
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
