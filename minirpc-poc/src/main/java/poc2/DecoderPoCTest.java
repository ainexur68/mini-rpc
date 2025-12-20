package poc2;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import java.util.ArrayList;
import java.util.List;

public class DecoderPoCTest {

    static final int FIXED_HEADER_LEN = 22;
    static final short MAGIC = (short) 0xCAFE;

    public static void main(String[] args) throws Exception {
        MiniRpcDecoder decoder = new MiniRpcDecoder();
        ByteBuf buf = Unpooled.buffer();

        List<Object> out = new ArrayList<>();

        byte[] frame1 = buildFrame(100, 0, 5);
        byte[] frame2 = buildFrame(200, 2, 20);

        // --------- 模拟半包 ---------
        buf.writeBytes(frame1, 0, 10); // 只写 10 字节
        decoderDecode(decoder, buf, out); // 预期：不输出任何帧
        System.out.println("After half frame, out=" + out);

        // --------- 写入剩余（完整的帧）---------
        buf.writeBytes(frame1, 10, frame1.length - 10); // 只写 10 字节
        decoderDecode(decoder, buf, out); // 预期：输出 1 帧
        System.out.println("After full frame, out=" + out);

        // --------- 模拟粘包（两个 frame 一起塞进去）---------
        buf.writeBytes(frame2); // 只写 10 字节
        decoderDecode(decoder, buf, out); // 预期：输出 1 帧
        System.out.println("After sticky frames, out=" + out);

        // ===== 结束 =====
        System.out.println("\nFinal result:");
        out.forEach(System.out::println);
    }

    static byte[] buildFrame(int requestId, int headerLen, int bodyLen) {
        ByteBuf buf = Unpooled.buffer(FIXED_HEADER_LEN + headerLen + bodyLen);
        buf.writerIndex(0);
        buf.writeShort(MAGIC);
        buf.writeByte(1);
        buf.writeByte(0);
        buf.writeShort(1);
        buf.writeLong(requestId);
        buf.writeInt(headerLen);
        buf.writeInt(bodyLen);

        for (int i = 0; i < headerLen; i++) {
            buf.writeByte(0);
        }

        for (int i = 0; i < bodyLen; i++) {
            buf.writeByte(i);
        }

        byte[] arr = new byte[buf.readableBytes()];
        buf.readBytes(arr);
        return arr;
    }

    static void decoderDecode(MiniRpcDecoder decoder, ByteBuf buf, List<Object> out) throws Exception {
        decoder.decode(null, buf, out);
//        decoder.decodeLast(null, buf, out);
    }
}
