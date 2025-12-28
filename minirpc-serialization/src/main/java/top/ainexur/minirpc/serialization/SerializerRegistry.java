package top.ainexur.minirpc.serialization;

import top.ainexur.minirpc.common.RpcErrorCode;
import top.ainexur.minirpc.common.RpcException;

import java.util.Map;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;

public class SerializerRegistry {

    private final Map<Byte, Serializer> byType = new ConcurrentHashMap<>();

    public SerializerRegistry() {
        ServiceLoader.load(Serializer.class)
                .forEach(this::register);
    }

    private void register(Serializer serializer) {
        byte type = serializer.serializeType();
        Serializer prev = byType.putIfAbsent(type, serializer);
        if (prev != null) {
            throw new IllegalStateException(
                    "Duplicate Serializer type=" + type +
                            ", prev=" + prev.getClass().getName() +
                            ", now=" + serializer.getClass().getName()
            );
        }
    }

    public Serializer required(byte type) {
        Serializer serializer = byType.get(type);
        if (serializer == null) {
            throw new RpcException(RpcErrorCode.UNSUPPORTED_SERIALIZE_TYPE, "No serializer for type=" + type);
        }
        return serializer;
    }
}
