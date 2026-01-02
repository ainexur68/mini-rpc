package top.ainexur.minirpc.common;

/**
 * RPC 领域异常，携带业务错误码。
 */
public final class RpcException extends RuntimeException {
    private final RpcErrorCode code;

    /**
     * 构造异常。
     *
     * @param code    错误码
     * @param message 错误信息
     */
    public RpcException(RpcErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    /**
     * 构造异常并携带原因。
     *
     * @param code    错误码
     * @param message 错误信息
     * @param cause   原因异常
     */
    public RpcException(RpcErrorCode code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    /**
     * 获取错误码。
     *
     * @return 错误码
     */
    public RpcErrorCode code() {
        return code;
    }
}
