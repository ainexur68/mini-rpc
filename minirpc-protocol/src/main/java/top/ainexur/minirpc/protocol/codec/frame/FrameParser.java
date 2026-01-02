package top.ainexur.minirpc.protocol.codec.frame;

import top.ainexur.minirpc.common.RpcErrorCode;
import top.ainexur.minirpc.common.RpcException;
import top.ainexur.minirpc.protocol.MiniRpcProtocol;
import top.ainexur.minirpc.protocol.frame.MiniRpcFrame;

/**
 * 协议帧解析器，将字节数组解析为 MiniRpcFrame。
 */
public final class FrameParser {
    /**
     * 解析协议帧字节数组。
     *
     * @param frameBytes 帧字节数组
     * @return 协议帧对象
     */
    public MiniRpcFrame parse(byte[] frameBytes) {
        if (frameBytes == null || frameBytes.length < MiniRpcProtocol.FIXED_HEADER_SIZE) {
            throw new RpcException(RpcErrorCode.BAD_REQUEST, "frame too short");
        }

        short magic = readShort(frameBytes, 0);
        if (magic != MiniRpcProtocol.MAGIC) {
            throw new RpcException(RpcErrorCode.BAD_REQUEST, "bad magic=" + magic);
        }

        byte version = frameBytes[2];
        if (version != MiniRpcProtocol.VERSION) {
            throw new RpcException(RpcErrorCode.BAD_REQUEST, "bad version=" + version);
        }

        byte serializeType = frameBytes[3];
        short flags = readShort(frameBytes, 4);
        long requestId = readLong(frameBytes, 6);
        int headerLen = readInt(frameBytes, 14);
        int bodyLen = readInt(frameBytes, 18);

        if (headerLen < 0 || bodyLen < 0 || headerLen + (long) bodyLen > MiniRpcProtocol.MAX_FRAME_SIZE) {
            throw new RpcException(RpcErrorCode.TOO_LARGE_REQUEST, "headerLen=" + headerLen + ", bodyLen=" + bodyLen);
        }

        int expectedLen = MiniRpcProtocol.FIXED_HEADER_SIZE + headerLen + bodyLen;
        if (frameBytes.length != expectedLen) {
            throw new RpcException(RpcErrorCode.BAD_REQUEST, "frame length mismatch");
        }

        int headerStart = MiniRpcProtocol.FIXED_HEADER_SIZE;
        byte[] extHeaders = headerLen == 0 ? new byte[0] : slice(frameBytes, headerStart, headerLen);
        int bodyStart = headerStart + headerLen;
        byte[] body = bodyLen == 0 ? new byte[0] : slice(frameBytes, bodyStart, bodyLen);

        return new MiniRpcFrame(serializeType, flags, requestId, extHeaders, body);
    }

    private static short readShort(byte[] bytes, int offset) {
        return (short) (((bytes[offset] & 0xff) << 8) | (bytes[offset + 1] & 0xff));
    }

    private static int readInt(byte[] bytes, int offset) {
        return ((bytes[offset] & 0xff) << 24)
                | ((bytes[offset + 1] & 0xff) << 16)
                | ((bytes[offset + 2] & 0xff) << 8)
                | (bytes[offset + 3] & 0xff);
    }

    private static long readLong(byte[] bytes, int offset) {
        return ((long) (bytes[offset] & 0xff) << 56)
                | ((long) (bytes[offset + 1] & 0xff) << 48)
                | ((long) (bytes[offset + 2] & 0xff) << 40)
                | ((long) (bytes[offset + 3] & 0xff) << 32)
                | ((long) (bytes[offset + 4] & 0xff) << 24)
                | ((long) (bytes[offset + 5] & 0xff) << 16)
                | ((long) (bytes[offset + 6] & 0xff) << 8)
                | ((long) (bytes[offset + 7] & 0xff));
    }

    private static byte[] slice(byte[] bytes, int offset, int length) {
        byte[] out = new byte[length];
        System.arraycopy(bytes, offset, out, 0, length);
        return out;
    }
}
