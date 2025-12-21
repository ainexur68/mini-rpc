package top.ainexur.minirpc.common;

public final class RpcException extends RuntimeException {
    private final RpcErrorCode code;

    public RpcException(RpcErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    public RpcException(RpcErrorCode code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public RpcErrorCode code() {
        return code;
    }
}
