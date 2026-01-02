package poc4;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import poc3.OtherRequest;
import poc3.RpcRequest;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;

/**
 * PoC：虚拟线程并发下的 Kryo 不安全示例。
 */
public class KryoVirtualThreadBadPoC {

    // ❌ 故意错误：共享一个 Kryo 实例供所有虚拟线程使用
    private static final Kryo KRYO = new Kryo();

    static {
        // 方便演示：不要求预注册，交给 Kryo 动态处理
        KRYO.setRegistrationRequired(false);
        // 你也可以尝试手动注册，看行为有没有差别
        // KRYO.register(RpcRequest.class, 10);
        // KRYO.register(OtherRequest.class, 11);
    }

    /**
     * 应用入口。
     *
     * @param args 参数
     * @throws Exception 异常
     */
    public static void main(String[] args) throws Exception {
        int total = Integer.getInteger("poc.total", 200_000); // 次数越大，越容易暴露问题
        int maxConcurrency = Integer.getInteger("poc.maxConcurrency", total);
        System.out.println("Start BAD Kryo PoC with Virtual Threads, total = " + total);

        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

        // 使用虚拟线程提交大量任务
        var start = new CountDownLatch(1);
        var done = new CountDownLatch(total);
        var limit = new Semaphore(maxConcurrency);
        var ok = new AtomicLong();
        var corrupted = new AtomicLong();
        var errors = new AtomicLong();
        var serializeErrors = new AtomicLong();
        var deserializeErrors = new AtomicLong();
        var errorSample = new AtomicReference<String>();
        var corruptedSample = new AtomicReference<String>();
        long begin = System.currentTimeMillis();
        try (executor) {
            IntStream.range(0, total).forEach(i -> executor.submit(() -> {
                try {
                    start.await();
                    limit.acquire();
                    runOnce(i);
                    ok.incrementAndGet();
                } catch (IllegalStateException e) {
                    corrupted.incrementAndGet();
                    corruptedSample.compareAndSet(null, e.getMessage());
                } catch (Exception e) {
                    errors.incrementAndGet();
                    if (isSerializeError(e)) {
                        serializeErrors.incrementAndGet();
                    } else if (isDeserializeError(e)) {
                        deserializeErrors.incrementAndGet();
                    }
                    errorSample.compareAndSet(null, e.getClass().getName() + ": " + e.getMessage());
                } finally {
                    limit.release();
                    done.countDown();
                }
            }));
            start.countDown();
            done.await();
        }

        long cost = System.currentTimeMillis() - begin;
        System.out.println("Bad Kryo VT PoC finished. ok=" + ok.get()
                + " corrupted=" + corrupted.get()
                + " errors=" + errors.get()
                + " serializeErr=" + serializeErrors.get()
                + " deserializeErr=" + deserializeErrors.get()
                + " costMs=" + cost);
        String sample = errorSample.get();
        if (sample != null) {
            System.out.println("Error sample: " + sample);
        }
        String corrupt = corruptedSample.get();
        if (corrupt != null) {
            System.out.println("Corrupted sample: " + corrupt);
        }
    }

    private static void runOnce(int i) {
        ThreadLocalRandom random = ThreadLocalRandom.current();

        boolean useA = random.nextBoolean();
        Object original;

        if (useA) {
            original = new RpcRequest(
                    "HelloService",
                    "hi",
                    new Object[]{"user-" + i, i, random.nextInt()}
            );
        } else {
            original = new OtherRequest(
                    i,
                    "payload-" + i + "-" + random.nextInt()
            );
        }

        // 序列化 + 反序列化
        byte[] bytes = serialize(original);
        Object back = deserialize(bytes, useA ? RpcRequest.class : OtherRequest.class);

        // 强校验：结构错 / 类型错 / 字段错，全部视为数据损坏
        if (useA) {
            if (!(back instanceof RpcRequest)) {
                fail("Expect RpcRequest, but got " + safeToStr(back), i);
            }
            RpcRequest r = (RpcRequest) back;
            if (!"HelloService".equals(r.service)
                    || !"hi".equals(r.method)
                    || r.args == null
                    || r.args.length != 3) {
                fail("RpcRequest corrupted, back=" + safeToStr(r), i);
            }
        } else {
            if (!(back instanceof OtherRequest)) {
                fail("Expect OtherRequest, but got " + safeToStr(back), i);
            }
            OtherRequest r = (OtherRequest) back;
            if (r.id != i || r.payload == null || !r.payload.startsWith("payload-" + i)) {
                fail("OtherRequest corrupted, back=" + safeToStr(r), i);
            }
        }
    }

    private static byte[] serialize(Object obj) {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             Output out = new Output(bos)) {

            // ① 在序列化前后故意“打断”虚拟线程，制造并发窗口
            Thread.yield();
            Thread.sleep(0); // 让调度器更有机会切换到其他虚拟线程

            KRYO.writeObject(out, obj);

            Thread.yield();
            Thread.sleep(0);

            out.flush();
            return bos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("serialize error", e);
        }
    }

    private static Object deserialize(byte[] bytes, Class<?> type) {
        try (ByteArrayInputStream bis = new ByteArrayInputStream(bytes);
             Input in = new Input(bis)) {

            // 同样在反序列化前后制造调度点
            Thread.yield();
            Thread.sleep(0);

            Object obj = KRYO.readObject(in, type);

            Thread.yield();
            Thread.sleep(0);

            return obj;
        } catch (Exception e) {
            throw new RuntimeException("deserialize error", e);
        }
    }

    private static boolean isSerializeError(Exception e) {
        String msg = e.getMessage();
        if (msg != null && msg.contains("serialize error")) {
            return true;
        }
        Throwable cause = e.getCause();
        return cause != null && cause.getMessage() != null && cause.getMessage().contains("serialize error");
    }

    private static boolean isDeserializeError(Exception e) {
        String msg = e.getMessage();
        if (msg != null && msg.contains("deserialize error")) {
            return true;
        }
        Throwable cause = e.getCause();
        return cause != null && cause.getMessage() != null && cause.getMessage().contains("deserialize error");
    }

    private static void fail(String msg, int i) {
        System.err.println(">>>> Data corrupted at i = " + i + ", msg = " + msg);
        // 直接抛异常终止整个 PoC
        throw new IllegalStateException(msg);
    }

    private static String safeToStr(Object o) {
        if (o == null) return "null";
        if (o instanceof RpcRequest r) {
            return "RpcRequest{service=" + r.service +
                    ", method=" + r.method +
                    ", argsLen=" + (r.args == null ? -1 : r.args.length) + "}";
        }
        if (o instanceof OtherRequest r) {
            return "OtherRequest{id=" + r.id +
                    ", payload=" + r.payload + "}";
        }
        return o.getClass().getName() + "@" + Integer.toHexString(System.identityHashCode(o));
    }
}
