package top.ainexur.minirpc.protocol.codec;

import top.ainexur.minirpc.protocol.frame.MiniRpcFrame;
import top.ainexur.minirpc.protocol.message.RpcRequest;
import top.ainexur.minirpc.protocol.message.RpcResponse;

public interface MessageCodec {
    MiniRpcFrame encodeRequest(RpcRequest request);
    MiniRpcFrame encodeResponse(RpcResponse response);
    Object decode(MiniRpcFrame frame);
}
