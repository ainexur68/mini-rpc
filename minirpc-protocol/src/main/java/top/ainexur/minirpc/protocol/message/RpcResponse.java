package top.ainexur.minirpc.protocol.message;

import java.util.Map;

/**
 * RPC 响应消息。
 */
public record RpcResponse(
        long requestId,
        int code,
        Object returnValue,
        Map<String, String> attachments
) {}
