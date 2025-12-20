package poc1;

import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;

public class SimpleClient {
    public static void main(String[] args) throws InterruptedException {
        int total = 50000;
        int threads = 32;
        String ip = "127.0.0.1";
        int port = 9000;

        ExecutorService pool = Executors.newFixedThreadPool(threads);

        long start = System.currentTimeMillis();

        IntStream.range(0, total).forEach(i -> {
            pool.submit(() -> {
                try (Socket socket = new Socket(ip, port);
                     OutputStream out = socket.getOutputStream()){
                    String msg = String.format("Hello-%s\n", i);
                    out.write(msg.getBytes(StandardCharsets.UTF_8));
                    out.flush();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        });

        pool.shutdown();

        while (!pool.isTerminated()) {
            Thread.sleep(100);
        }

        long cost = System.currentTimeMillis() - start;

        System.out.println(String.format("Total cost: %d ms", cost));
    }
}
