package top.ainexur.minirpc.serialization;

/**
 * 序列化器接口。
 */
public interface Serializer {
    /**
     * 获取序列化器类型标识。
     *
     * @return 类型标识
     */
    byte serializeType();

    /**
     * 序列化对象。
     *
     * @param obj 对象
     * @return 字节数组
     */
    byte[] serialize(Object obj);

    /**
     * 反序列化字节数组。
     *
     * @param data 字节数组
     * @param type 目标类型
     * @param <T>  目标类型
     * @return 反序列化对象
     */
    <T> T deserialize(byte[] data, Class<T> type);
}
