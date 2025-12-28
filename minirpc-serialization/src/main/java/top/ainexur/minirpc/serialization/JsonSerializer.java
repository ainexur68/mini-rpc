package top.ainexur.minirpc.serialization;

import com.fasterxml.jackson.databind.ObjectMapper;
import top.ainexur.minirpc.common.RpcErrorCode;
import top.ainexur.minirpc.common.RpcException;

import java.io.IOException;

public class JsonSerializer implements Serializer {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public byte serializeType() {
        return 0;
    }

    @Override
    public byte[] serialize(Object obj) {
        try {
            return MAPPER.writeValueAsBytes(obj);
        } catch (IOException exp) {
            throw new RpcException(RpcErrorCode.SERIALIZE_ERROR, exp.getMessage());
        }
    }

    @Override
    public <T> T deserialize(byte[] data, Class<T> type) {
        try {
            return MAPPER.readValue(data, type);
        } catch (IOException exp) {
            throw new RpcException(RpcErrorCode.DESERIALIZE_ERROR, exp.getMessage());
        }
    }
}
