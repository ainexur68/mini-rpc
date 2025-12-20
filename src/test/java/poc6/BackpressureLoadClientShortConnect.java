package poc6;

import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.IntStream;

public class BackpressureLoadClientShortConnect {
    public static void main(String[] args) throws Exception {
        int total = 20000;
        int threads = 200;
        String host = "127.0.0.1";
        int port = 9100;

        var pool = Executors.newFixedThreadPool(threads);
        long start = System.currentTimeMillis();
        IntStream.range(0, total).forEach(i -> pool.submit(() -> sendOnce(host, port, i)));
        pool.shutdown();
        while (!pool.isTerminated()) {
            Thread.sleep(100);
        }
        System.out.println("cost=" + (System.currentTimeMillis() - start) + "ms");
    }

    private static void sendOnce(String host, int port, int i) {
        try (Socket socket = new Socket(host, port);
             OutputStream out = socket.getOutputStream()) {
            String msg = "hi-" + i + "-" + ThreadLocalRandom.current().nextInt() + "\n";
            out.write(msg.getBytes(StandardCharsets.UTF_8));
            out.flush();
        } catch (Exception e) {
            System.err.println("send fail i=" + i + " err=" + e.getMessage());
        }
    }
}
