package poc3;

import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.IntStream;

public class KryoPoC {
    public static void main(String[] args) {
        int total = 200000;

        IntStream.range(0, total).parallel().forEach(KryoPoC::check);

        System.out.println("GOOD Kryo PoC finished, no corruption.");
    }

    private static void check(int i) {
        RpcRequest rpcRequest = new RpcRequest("HelloService",
                "hi",
                new Object[]{"user-" + i, i, ThreadLocalRandom.current().nextInt()}
        );
        byte[] bytes = KryoSafeSupport.serialize(rpcRequest);
        RpcRequest back = KryoSafeSupport.deserialize(bytes, RpcRequest.class);
        if (back == null ||
                !"HelloService".equals(back.service) ||
                !"hi".equals(back.method) ||
                back.args == null ||
                back.args.length != 3) {
            System.err.println("Data corrupted at i = " + i + ", back=" + toStr(back));
            throw new IllegalStateException("Kryo data corruption found (GOOD PoC)");
        } else {
                System.out.println(i + ": ok");
        }
    }

    private static String toStr(RpcRequest r) {
        if (r == null) return "null";
        return "RpcRequest{service=" + r.service +
                ", method=" + r.method +
                ", argsLen=" + (r.args == null ? -1 : r.args.length) + "}";
    }
}
