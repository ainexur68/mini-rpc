package top.ainexur.minirpc.protocol.message;

import java.util.Map;

public record RpcResponse(
        long requestId,
        int code,
        Object returnValue,
        Map<String, String> attachments
) {}
