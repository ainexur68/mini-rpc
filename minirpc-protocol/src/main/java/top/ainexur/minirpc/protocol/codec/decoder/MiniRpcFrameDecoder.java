package top.ainexur.minirpc.protocol.codec.decoder;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import top.ainexur.minirpc.common.RpcErrorCode;
import top.ainexur.minirpc.common.RpcException;
import top.ainexur.minirpc.protocol.frame.MiniRpcFrame;
import top.ainexur.minirpc.protocol.MiniRpcProtocol;

import java.util.List;

public class MiniRpcFrameDecoder extends ByteToMessageDecoder {
    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
        // rely on ByteToMessageDecoder to re-invoke decode for sticky packets
        if (in.readableBytes() < MiniRpcProtocol.FIXED_HEADER_SIZE) {
            return;
        }
        in.markReaderIndex();
        short magic = in.readShort();
        if (magic != MiniRpcProtocol.MAGIC) {
            ctx.close();
            throw new RpcException(RpcErrorCode.BAD_REQUEST, "bad magic=" + magic);
        }

        byte version = in.readByte();
        if (version != MiniRpcProtocol.VERSION) {
            ctx.close();
            throw new RpcException(RpcErrorCode.BAD_REQUEST, "bad version=" + version);
        }

        byte serializeType = in.readByte();
        short flags = in.readShort();
        long requestId = in.readLong();
        int headerLen = in.readInt();
        int bodyLen = in.readInt();
        if (headerLen < 0 || bodyLen < 0 || headerLen + (long) bodyLen > MiniRpcProtocol.MAX_FRAME_SIZE) {
            ctx.close();
            throw new RpcException(RpcErrorCode.TOO_LARGE_REQUEST, "headerLen=" + headerLen + ", bodyLen=" + bodyLen);
        }

        if (in.readableBytes() < headerLen + bodyLen) {
            in.resetReaderIndex();
            return;
        }

        byte[] extHeaders = new byte[headerLen];
        if (headerLen > 0) {
            in.readBytes(extHeaders);
        }
        byte[] body = new byte[bodyLen];
        in.readBytes(body);

        out.add(
            new MiniRpcFrame(
                serializeType,
                flags,
                requestId,
                extHeaders,
                body)
        );
    }
}
