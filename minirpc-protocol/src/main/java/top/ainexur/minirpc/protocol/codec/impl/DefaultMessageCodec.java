package top.ainexur.minirpc.protocol.codec.impl;

import top.ainexur.minirpc.common.FlagBits;
import top.ainexur.minirpc.protocol.codec.MessageCodec;
import top.ainexur.minirpc.protocol.frame.MiniRpcFrame;
import top.ainexur.minirpc.protocol.message.RpcRequest;
import top.ainexur.minirpc.protocol.message.RpcResponse;
import top.ainexur.minirpc.serialization.Serializer;
import top.ainexur.minirpc.serialization.SerializerRegistry;

public class DefaultMessageCodec implements MessageCodec {
    private final SerializerRegistry registry;
    private final byte defaultSerializeType;

    public DefaultMessageCodec() {
        this(new SerializerRegistry(), (byte) 0);
    }

    public DefaultMessageCodec(byte defaultSerializeType) {
        this(new SerializerRegistry(), defaultSerializeType);
    }

    public DefaultMessageCodec(SerializerRegistry registry, byte defaultSerializeType) {
        this.registry = registry;
        this.defaultSerializeType = defaultSerializeType;
    }

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
