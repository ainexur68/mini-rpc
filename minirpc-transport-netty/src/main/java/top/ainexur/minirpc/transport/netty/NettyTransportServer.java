package top.ainexur.minirpc.transport.netty;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import top.ainexur.minirpc.protocol.codec.MessageCodec;
import top.ainexur.minirpc.protocol.codec.impl.DefaultMessageCodec;
import top.ainexur.minirpc.transport.RequestHandler;
import top.ainexur.minirpc.transport.TransportServer;
import top.ainexur.minirpc.transport.netty.codec.NettyFrameEncoder;
import top.ainexur.minirpc.transport.netty.codec.NettyFrameSlicer;
import top.ainexur.minirpc.transport.netty.codec.NettyMessageDecoder;
import top.ainexur.minirpc.transport.netty.codec.NettyMessageEncoder;
import top.ainexur.minirpc.transport.netty.handler.ServerRequestHandler;

import java.net.InetSocketAddress;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 基于 Netty 的服务端实现，接收请求并在业务线程中处理。
 */
public class NettyTransportServer implements TransportServer {
    private final RequestHandler handler;
    private final MessageCodec codec;
    private final EventLoopGroup bossGroup = new NioEventLoopGroup(1);
    private final EventLoopGroup workerGroup = new NioEventLoopGroup();
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private Channel channel;
    private volatile int boundPort = -1;

    /**
     * 构造服务端，使用默认消息编解码器。
     *
     * @param handler 请求处理器
     */
    public NettyTransportServer(RequestHandler handler) {
        this(handler, new DefaultMessageCodec());
    }

    /**
     * 构造服务端。
     *
     * @param handler 请求处理器
     * @param codec   消息编解码器
     */
    public NettyTransportServer(RequestHandler handler, MessageCodec codec) {
        this.handler = Objects.requireNonNull(handler, "handler");
        this.codec = Objects.requireNonNull(codec, "codec");
    }

    /**
     * 启动服务并绑定端口。
     *
     * @param port 监听端口，0 表示随机端口
     */
    @Override
    public void start(int port) {
        ServerBootstrap bootstrap = new ServerBootstrap();
        bootstrap.group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ChannelPipeline pipeline = ch.pipeline();
                        pipeline.addLast(new NettyFrameSlicer());
                        pipeline.addLast(new NettyMessageDecoder(codec));
                        pipeline.addLast(new ServerRequestHandler(handler, executor));
                        pipeline.addLast(new NettyFrameEncoder());
                        pipeline.addLast(new NettyMessageEncoder(codec));
                    }
                });
        channel = bootstrap.bind(port).syncUninterruptibly().channel();
        boundPort = ((InetSocketAddress) channel.localAddress()).getPort();
    }

    /**
     * 停止服务并释放资源。
     */
    @Override
    public void stop() {
        if (channel != null) {
            channel.close().syncUninterruptibly();
            channel = null;
        }
        bossGroup.shutdownGracefully();
        workerGroup.shutdownGracefully();
        executor.close();
    }

    /**
     * 返回已绑定端口。
     *
     * @return 监听端口
     */
    @Override
    public int port() {
        return boundPort;
    }
}
