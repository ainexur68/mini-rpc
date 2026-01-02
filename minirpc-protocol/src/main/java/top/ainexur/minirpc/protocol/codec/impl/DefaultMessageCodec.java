package top.ainexur.minirpc.protocol.codec.impl;

import top.ainexur.minirpc.common.FlagBits;
import top.ainexur.minirpc.protocol.codec.MessageCodec;
import top.ainexur.minirpc.protocol.frame.MiniRpcFrame;
import top.ainexur.minirpc.protocol.message.RpcRequest;
import top.ainexur.minirpc.protocol.message.RpcResponse;
import top.ainexur.minirpc.serialization.Serializer;
import top.ainexur.minirpc.serialization.SerializerRegistry;

/**
 * 默认消息编解码实现，基于 SerializerRegistry。
 */
public class DefaultMessageCodec implements MessageCodec {
    private final SerializerRegistry registry;
    private final byte defaultSerializeType;

    /**
     * 使用默认序列化类型构造编解码器。
     */
    public DefaultMessageCodec() {
        this(new SerializerRegistry(), (byte) 0);
    }

    /**
     * 使用指定默认序列化类型构造编解码器。
     *
     * @param defaultSerializeType 默认序列化类型
     */
    public DefaultMessageCodec(byte defaultSerializeType) {
        this(new SerializerRegistry(), defaultSerializeType);
    }

    /**
     * 构造编解码器。
     *
     * @param registry             序列化器注册表
     * @param defaultSerializeType 默认序列化类型
     */
    public DefaultMessageCodec(SerializerRegistry registry, byte defaultSerializeType) {
        this.registry = registry;
        this.defaultSerializeType = defaultSerializeType;
    }

    /**
     * 编码请求为协议帧。
     *
     * @param request 请求对象
     * @return 协议帧
     */
    @Override
    public MiniRpcFrame encodeRequest(RpcRequest request) {
        Serializer serializer = registry.required(defaultSerializeType);
        byte[] body = serializer.serialize(request);
        return new MiniRpcFrame(
                serializer.serializeType(),
                (short) 0,
                request.requestId(),
                null,
                body
        );
    }

    /**
     * 编码响应为协议帧。
     *
     * @param response 响应对象
     * @return 协议帧
     */
    @Override
    public MiniRpcFrame encodeResponse(RpcResponse response) {
        Serializer serializer = registry.required(defaultSerializeType);
        byte[] body = serializer.serialize(response);
        return new MiniRpcFrame(
                serializer.serializeType(),
                FlagBits.RESPONSE,
                response.requestId(),
                null,
                body
        );
    }

    /**
     * 解码协议帧为请求或响应对象。
     *
     * @param frame 协议帧
     * @return 解码后的对象
     */
    @Override
    public Object decode(MiniRpcFrame frame) {
        Serializer serializer = registry.required(frame.serializeType());
        short flags = frame.flags();

        boolean isResponse = (flags & FlagBits.RESPONSE) != 0;
        boolean isHeartbeat = (flags & FlagBits.HEARTBEAT) != 0;

        byte[] body = frame.body();

        if (isHeartbeat && (body == null || body.length == 0)) {
            return null;
        }

        if (isResponse) {
            return serializer.deserialize(body, RpcResponse.class);
        }

        return serializer.deserialize(body, RpcRequest.class);
    }
}
