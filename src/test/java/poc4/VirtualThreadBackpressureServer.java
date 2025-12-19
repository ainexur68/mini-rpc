package poc4;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicLong;

/**
 * PoC: 在 Netty IO 线程与虚拟线程业务之间加上背压/拒绝控制，验证虚拟线程不会“放飞”导致内存/CPU 撑爆。
 */
public class VirtualThreadBackpressureServer {

    private static final int PORT = Integer.getInteger("poc.port", 9100);
    private static final int MAX_IN_FLIGHT = Integer.getInteger("poc.maxInFlight", 1000);
    private static final Duration BIZ_COST = Duration.ofMillis(Long.getLong("poc.bizMs", 10L));

    public static void main(String[] args) throws Exception {
        var boss = new NioEventLoopGroup(1);
        var worker = new NioEventLoopGroup();
        var vts = Executors.newVirtualThreadPerTaskExecutor();
        var permits = new Semaphore(MAX_IN_FLIGHT);
        var rejected = new AtomicLong();
        var ok = new AtomicLong();

        try (vts) {
            var bootstrap = new ServerBootstrap()
                    .group(boss, worker)
                    .channel(NioServerSocketChannel.class)
                    .option(ChannelOption.SO_BACKLOG, 8192)
                    .childOption(ChannelOption.SO_REUSEADDR, true)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ch.pipeline().addLast(new SimpleChannelInboundHandler<io.netty.buffer.ByteBuf>() {
                                @Override
                                protected void channelRead0(ChannelHandlerContext ctx, io.netty.buffer.ByteBuf msg) {
                                    String body = msg.toString(StandardCharsets.UTF_8).trim();
                                    if (!permits.tryAcquire()) {
                                        // 超限立即拒绝，防止虚拟线程无限膨胀
                                        rejected.incrementAndGet();
                                        ctx.writeAndFlush(Unpooled.wrappedBuffer("BUSY\n".getBytes(StandardCharsets.UTF_8)));
                                        return;
                                    }
                                    vts.submit(() -> {
                                        try {
                                            Thread.sleep(BIZ_COST.toMillis());
                                            ok.incrementAndGet();
                                            ctx.writeAndFlush(Unpooled.wrappedBuffer(("OK:" + body + "\n").getBytes(StandardCharsets.UTF_8)));
                                        } catch (InterruptedException ignored) {
                                            Thread.currentThread().interrupt();
                                        } finally {
                                            permits.release();
                                        }
                                    });
                                }
                            });
                        }
                    });

            ChannelFuture f = bootstrap.bind(PORT).sync();
            System.out.println("Backpressure server started on port " + PORT);
            Runtime.getRuntime().addShutdownHook(new Thread(() ->
                    System.out.printf("stat ok=%d rejected=%d%n", ok.get(), rejected.get())));
            f.channel().closeFuture().sync();
        } finally {
            boss.shutdownGracefully();
            worker.shutdownGracefully();
        }
    }
}
