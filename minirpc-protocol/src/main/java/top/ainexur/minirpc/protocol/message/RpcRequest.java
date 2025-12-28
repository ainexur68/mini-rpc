package top.ainexur.minirpc.protocol.message;

import java.util.Map;

public record RpcRequest (
        long requestId,
        String interfaceName,
        String methodName,
        String[] paramTypeNames,
        Object[] args,
        Map<String, String> attachments
) {
}
