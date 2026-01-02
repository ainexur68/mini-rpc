package top.ainexur.minirpc.transport.netty.codec;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import top.ainexur.minirpc.common.RpcErrorCode;
import top.ainexur.minirpc.common.RpcException;
import top.ainexur.minirpc.protocol.MiniRpcProtocol;
import top.ainexur.minirpc.protocol.codec.frame.FrameParser;
import top.ainexur.minirpc.protocol.frame.MiniRpcFrame;

import java.util.List;

/**
 * Netty 解码器，将字节流切分为完整的 MiniRpcFrame。
 */
public class NettyFrameSlicer extends ByteToMessageDecoder {
    private final FrameParser parser = new FrameParser();

    /**
     * 按固定头长度与扩展头/体长度切分帧。
     *
     * @param ctx 上下文
     * @param in  输入缓冲区
     * @param out 输出对象列表
     */
    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
        if (in.readableBytes() < MiniRpcProtocol.FIXED_HEADER_SIZE) {
            return;
        }

        int readerIdx = in.readerIndex();
        int headerLen = in.getInt(readerIdx + 14);
        int bodyLen = in.getInt(readerIdx + 18);

        if (headerLen < 0 || bodyLen < 0 || headerLen + (long) bodyLen > MiniRpcProtocol.MAX_FRAME_SIZE) {
            throw new RpcException(RpcErrorCode.TOO_LARGE_REQUEST, "headerLen=" + headerLen + ", bodyLen=" + bodyLen);
        }

        int frameSize = MiniRpcProtocol.FIXED_HEADER_SIZE + headerLen + bodyLen;
        if (in.readableBytes() < frameSize) {
            return;
        }

        byte[] frameBytes = new byte[frameSize];
        in.readBytes(frameBytes);
        MiniRpcFrame frame = parser.parse(frameBytes);
        out.add(frame);
    }
}
