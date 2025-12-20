package poc3;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.IntStream;

public class KryoBadPoC {

    // ❶ 错误点：一个静态单例，被多个线程共享
    private static final Kryo KRYO = new Kryo();

    static {
        // 没有注册类型，也不显式配置兼容序列化，先故意简单点
        KRYO.setRegistrationRequired(false);
    }

    public static void main(String[] args) {
        int total = 300000; // 提高次数，提升复现确定性

        System.out.println("Start bad Kryo PoC, total = " + total);

        IntStream.range(0, total).parallel().forEach(i -> {
            RpcRequest req = new RpcRequest(
                    "HelloService",
                    "hi",
                    new Object[]{"user-" + i, i, ThreadLocalRandom.current().nextInt()}
            );

            // ❷ 多线程并发共享同一个 KRYO + Output/Input → 高危
            byte[] bytes = serialize(req);
            RpcRequest back = deserialize(bytes);

            // ❸ 校验字段是否被“搞乱”
            if (!"HelloService".equals(back.service) ||
                    !"hi".equals(back.method) ||
                    back.args == null ||
                    back.args.length != 3) {
                System.err.println("Data corrupted at i = " + i + ", back=" + toStr(back));
                throw new IllegalStateException("Kryo data corruption found");
            }
        });

        System.out.println("Bad Kryo PoC finished (if no exception, try increasing total or run several times).");
    }

    private static byte[] serialize(RpcRequest req) {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             Output out = new Output(bos)) {
            KRYO.writeObject(out, req);
            out.flush();
            return bos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static RpcRequest deserialize(byte[] bytes) {
        try (ByteArrayInputStream bis = new ByteArrayInputStream(bytes);
             Input in = new Input(bis)) {
            return KRYO.readObject(in, RpcRequest.class);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static String toStr(RpcRequest r) {
        if (r == null) return "null";
        return "RpcRequest{service=" + r.service +
                ", method=" + r.method +
                ", argsLen=" + (r.args == null ? -1 : r.args.length) + "}";
    }
}
