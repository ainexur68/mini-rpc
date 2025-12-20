package poc6;

import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.IntStream;

/**
 * 简单压测客户端：大量并发连接到背压服务器，观测拒绝/通过情况。
 */
public class BackpressureLoadClient {
    public static void main(String[] args) throws Exception {
        int total = getIntArg(args, 0, 50000);   // 请求总数
        int threads = getIntArg(args, 1, 100);   // 发送线程数
        int connections = getIntArg(args, 2, 500); // 预先建立的长连接数量
        String host = getStrArg(args, 3, "127.0.0.1");
        int port = getIntArg(args, 4, 9100);

        var sockets = IntStream.range(0, connections)
                .mapToObj(i -> createSocket(host, port, i))
                .toArray(Socket[]::new);
        Object[] locks = IntStream.range(0, connections)
                .mapToObj(i -> new Object())
                .toArray(Object[]::new);

        var pool = Executors.newFixedThreadPool(threads);
        long start = System.currentTimeMillis();
        IntStream.range(0, total).forEach(i -> pool.submit(() -> sendOnce(sockets, locks, i)));
        pool.shutdown();
        while (!pool.isTerminated()) {
            Thread.sleep(100);
        }

        for (Socket socket : sockets) {
            try {
                socket.close();
            } catch (Exception ignored) {
            }
        }
        System.out.println("cost=" + (System.currentTimeMillis() - start) + "ms");
    }

    private static Socket createSocket(String host, int port, int idx) {
        try {
            return new Socket(host, port);
        } catch (Exception e) {
            throw new RuntimeException("create socket fail idx=" + idx, e);
        }
    }

    private static void sendOnce(Socket[] sockets, Object[] locks, int i) {
        int idx = i % sockets.length;
        Socket socket = sockets[idx];
        Object lock = locks[idx];
        String msg = "hi-" + i + "-" + ThreadLocalRandom.current().nextInt() + "\n";
        try {
            synchronized (lock) {
                OutputStream out = socket.getOutputStream();
                out.write(msg.getBytes(StandardCharsets.UTF_8));
                out.flush();
            }
        } catch (Exception e) {
            System.err.println("send fail i=" + i + " err=" + e.getMessage());
        }
    }

    private static int getIntArg(String[] args, int index, int defaultVal) {
        if (args.length > index) {
            try {
                return Integer.parseInt(args[index]);
            } catch (NumberFormatException ignored) {
            }
        }
        return defaultVal;
    }

    private static String getStrArg(String[] args, int index, String defaultVal) {
        if (args.length > index) {
            return args[index];
        }
        return defaultVal;
    }
}
