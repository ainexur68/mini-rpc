package poc2;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.DecoderException;

import java.util.List;

public class MiniRpcDecoder extends ByteToMessageDecoder {

    private static final int FIXED_HEADER_LEN = 22;

    private static final short MAGIC = (short) 0xCAFE;

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        while (true) {
            // 1. 固定头长度不足，不处理
            if (in.readableBytes() < FIXED_HEADER_LEN) {
                return;
            }

            in.markReaderIndex();

            // 2. 读取固定头
            short magic = in.readShort();
            byte version = in.readByte();
            byte type = in.readByte();
            short flag = in.readShort();
            long requestId = in.readLong();
            int headerLen = in.readInt();
            int bodyLen = in.readInt();

            // 3. 魔数校验，类型异常
            if (magic != MAGIC) {
                ctx.close();
                throw new DecoderException("MAGIC ERROR");
            }

            // 4. 安全校验（防止恶意 bodyLength 导致 OOM）
            if (headerLen < 0 || bodyLen < 0 || headerLen + bodyLen > 20 * 1024 * 1024) {
                ctx.close();
                throw new IllegalStateException("HeaderLength or BodyLength too large");
            }

            // 5. 半包（数据不够），回退 readerIndex
            if (in.readableBytes() < headerLen + bodyLen) {
                in.markReaderIndex();
                return;
            }

            // 6. 提取完整帧
            // 1.0跳过扩展头
            in.skipBytes(headerLen);

            byte[] body = new byte[bodyLen];
            if (bodyLen > 0) {
                in.readBytes(body);
            }

            // 7. 输出解码结果（这里先打印，后续换成你的 RpcRequest/RpcResponse）
            out.add("Frame{reqId=" + requestId +
                    ", hLen=" + headerLen +
                    ", bLen=" + bodyLen + "}");

        }
    }
}
