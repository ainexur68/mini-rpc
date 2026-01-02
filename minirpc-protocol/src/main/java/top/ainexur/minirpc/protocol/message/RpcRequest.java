package top.ainexur.minirpc.protocol.message;

import java.util.Map;

/**
 * RPC 请求消息。
 */
public record RpcRequest (
        long requestId,
        String interfaceName,
        String methodName,
        String[] paramTypeNames,
        Object[] args,
        Map<String, String> attachments
) {
}
