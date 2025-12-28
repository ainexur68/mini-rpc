package top.ainexur.minirpc.serialization;

public interface Serializer {
    byte serializeType(); // JSON=0
    byte[] serialize(Object obj);
    <T> T deserialize(byte[] data, Class<T> type);
}
