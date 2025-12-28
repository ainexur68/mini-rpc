package top.ainexur.minirpc.common;

public enum RpcErrorCode {
    OK(0),
    TIMEOUT(1),
    CONNECTION_CLOSED(2),
    BAD_REQUEST(3),
    SERVER_ERROR(4),
    DESERIALIZE_ERROR(5),
    SERIALIZE_ERROR(6),
    NO_PROVIDER(7),
    TOO_LARGE_REQUEST(10),
    UNSUPPORTED_SERIALIZE_TYPE(21);

    public final int code;

    RpcErrorCode(int code) {
        this.code = code;
    }
}
