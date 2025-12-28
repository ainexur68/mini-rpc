package top.ainexur.minirpc.protocol.frame;

import top.ainexur.minirpc.protocol.MiniRpcProtocol;

public record MiniRpcFrame(
    byte serializeType,
    short flags,
    long requestId,
    byte[] extHeader,
    byte[] body
){
    public static final short MAGIC = MiniRpcProtocol.MAGIC;
    public static final short VERSION = MiniRpcProtocol.VERSION;

    public int headerLength(){
        return extHeader == null ? 0 : extHeader.length;
    }
    public int bodyLength(){
        return body == null ? 0 : body.length;
    }

    public void validate() {
        long total = (long) headerLength() + bodyLength();
        if (total > MiniRpcProtocol.MAX_FRAME_SIZE) {
            throw new IllegalStateException("Frame too large: " + total);
        }
    }
}
