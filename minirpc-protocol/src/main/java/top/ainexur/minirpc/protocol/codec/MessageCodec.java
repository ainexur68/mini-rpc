package top.ainexur.minirpc.protocol.codec;

import top.ainexur.minirpc.protocol.frame.MiniRpcFrame;
import top.ainexur.minirpc.protocol.message.RpcRequest;
import top.ainexur.minirpc.protocol.message.RpcResponse;

/**
 * 消息编解码器，将请求/响应与协议帧互转。
 */
public interface MessageCodec {
    /**
     * 编码请求为协议帧。
     *
     * @param request 请求对象
     * @return 协议帧
     */
    MiniRpcFrame encodeRequest(RpcRequest request);

    /**
     * 编码响应为协议帧。
     *
     * @param response 响应对象
     * @return 协议帧
     */
    MiniRpcFrame encodeResponse(RpcResponse response);

    /**
     * 解码协议帧为请求或响应对象。
     *
     * @param frame 协议帧
     * @return 请求或响应对象
     */
    Object decode(MiniRpcFrame frame);
}
