package poc3;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class KryoSafeSupport {
    private static final ThreadLocal<Kryo> KRYO_LOCAL = ThreadLocal.withInitial(() -> {
        Kryo kryo = new Kryo();
        kryo.register(RpcRequest.class, 10);
        kryo.register(String.class, 11);
        kryo.register(Integer.class, 12);
        kryo.register(Object[].class, 13);
        kryo.setRegistrationRequired(true);
        return kryo;
    });

    public static byte[] serialize(Object obj) {
        Kryo kryo = KRYO_LOCAL.get();
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
            Output out = new Output(bos)) {
            kryo.writeObject(out, obj);
            out.flush();
            return bos.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Kryo serialize error", e);
        }
    }

    public static <T> T deserialize(byte[] arr, Class<T> type) {
        Kryo kryo = KRYO_LOCAL.get();
        try (ByteArrayInputStream bis = new ByteArrayInputStream(arr);
             Input in = new Input(bis)) {
            return kryo.readObject(in, type);
        } catch (IOException e) {
            throw new RuntimeException("Kryo deserialize error", e);
        }
    }
}
