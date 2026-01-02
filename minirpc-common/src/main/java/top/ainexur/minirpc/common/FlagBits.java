package top.ainexur.minirpc.common;

/**
 * 协议标志位常量定义。
 */
public final class FlagBits {
    public static final short HEARTBEAT = 1 << 0;
    public static final short COMPRESSED = 1 << 1;
    public static final short ENCRYPTED = 1 << 2;
    public static final short ONE_WAY = 1 << 3;
    public static final short RESPONSE = 1 << 4;

    private FlagBits() {}
}
