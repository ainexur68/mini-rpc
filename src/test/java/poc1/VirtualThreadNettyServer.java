package poc1;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;

public class VirtualThreadNettyServer {
    public static void main(String[] args) {
        // 1. Netty线程组
        var boos = new NioEventLoopGroup(1);
        var worker = new NioEventLoopGroup();

        // 2. 虚拟线程执行器（Java21）
        var vts = Executors.newVirtualThreadPerTaskExecutor();

        try {
            var b = new ServerBootstrap()
                    .group(boos, worker)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            System.out.println("pipeline executed by" + Thread.currentThread());
                            ch.pipeline().addLast(new SimpleChannelInboundHandler<ByteBuf>() {
                                @Override
                                protected void channelRead0(ChannelHandlerContext ctx, ByteBuf msg) throws Exception {
                                    String body = msg.toString(StandardCharsets.UTF_8).trim();
                                    System.out.printf("[IO ] thread=%s, recv=%s%n\n", Thread.currentThread().getName(), body);

                                    // 把真正的任务交给虚拟线程执行
                                    vts.submit(() -> {
                                        System.out.printf("[VT} start in %s\n", Thread.currentThread());
                                        try {
                                            Thread.sleep(10);
                                        } catch (InterruptedException e) {
                                        }
                                        System.out.printf("[VT} end in %s, req = %s\n", Thread.currentThread(), body);
                                    });
                                }

                                @Override
                                public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
                                    cause.printStackTrace();
                                    ctx.close();
                                }
                            });
                        }
                    });
            ChannelFuture f = b.bind(9000).sync();
            System.out.println("Netty VirtualThread PoC Server started on 9000");
            f.channel().closeFuture().sync();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
