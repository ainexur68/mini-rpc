package top.ainexur.minirpc.protocol.frame;

import top.ainexur.minirpc.protocol.MiniRpcProtocol;

/**
 * 协议帧结构，包含固定头信息与可变头/正文。
 */
public record MiniRpcFrame(
    byte serializeType,
    short flags,
    long requestId,
    byte[] extHeader,
    byte[] body
){
    public static final short MAGIC = MiniRpcProtocol.MAGIC;
    public static final short VERSION = MiniRpcProtocol.VERSION;

    /**
     * 获取扩展头长度。
     *
     * @return 扩展头长度
     */
    public int headerLength(){
        return extHeader == null ? 0 : extHeader.length;
    }

    /**
     * 获取消息体长度。
     *
     * @return 消息体长度
     */
    public int bodyLength(){
        return body == null ? 0 : body.length;
    }

    /**
     * 校验帧大小是否合法。
     */
    public void validate() {
        long total = (long) headerLength() + bodyLength();
        if (total > MiniRpcProtocol.MAX_FRAME_SIZE) {
            throw new IllegalStateException("Frame too large: " + total);
        }
    }
}
