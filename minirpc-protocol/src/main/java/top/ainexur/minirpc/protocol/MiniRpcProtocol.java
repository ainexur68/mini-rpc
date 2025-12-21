package top.ainexur.minirpc.protocol;

public final class MiniRpcProtocol {
    public static final short MAGIC = (short) 0xCAFE;
    public static final byte VERSION = 1;
    public static final int FIXED_HEADER_SIZE = 22;
    public static final long MAX_FRAME_SIZE = 1024 * 1024;
}
