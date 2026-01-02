package top.ainexur.minirpc.serialization;

import com.fasterxml.jackson.databind.ObjectMapper;
import top.ainexur.minirpc.common.RpcErrorCode;
import top.ainexur.minirpc.common.RpcException;

import java.io.IOException;

/**
 * 基于 Jackson 的 JSON 序列化器。
 */
public class JsonSerializer implements Serializer {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * 获取序列化类型标识。
     *
     * @return 类型标识
     */
    @Override
    public byte serializeType() {
        return 0;
    }

    /**
     * 序列化对象为 JSON 字节数组。
     *
     * @param obj 对象
     * @return 字节数组
     */
    @Override
    public byte[] serialize(Object obj) {
        try {
            return MAPPER.writeValueAsBytes(obj);
        } catch (IOException exp) {
            throw new RpcException(RpcErrorCode.SERIALIZE_ERROR, exp.getMessage());
        }
    }

    /**
     * 反序列化 JSON 字节数组为对象。
     *
     * @param data 字节数组
     * @param type 目标类型
     * @param <T>  目标类型
     * @return 反序列化对象
     */
    @Override
    public <T> T deserialize(byte[] data, Class<T> type) {
        try {
            return MAPPER.readValue(data, type);
        } catch (IOException exp) {
            throw new RpcException(RpcErrorCode.DESERIALIZE_ERROR, exp.getMessage());
        }
    }
}
