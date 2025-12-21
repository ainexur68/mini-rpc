package top.ainexur.minirpc.protocol.frame;

public record MiniRpcFrame(
    short magic,
    byte version,
    byte serializeType,
    short flags,
    long requestId,
    int headerLength,
    int bodyLength,
    byte[] extHeader,
    byte[] body
){}
