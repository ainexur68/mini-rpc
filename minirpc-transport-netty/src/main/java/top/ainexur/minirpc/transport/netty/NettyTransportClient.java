package top.ainexur.minirpc.transport.netty;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import top.ainexur.minirpc.common.RpcErrorCode;
import top.ainexur.minirpc.common.RpcException;
import top.ainexur.minirpc.protocol.codec.MessageCodec;
import top.ainexur.minirpc.protocol.codec.impl.DefaultMessageCodec;
import top.ainexur.minirpc.protocol.message.RpcRequest;
import top.ainexur.minirpc.protocol.message.RpcResponse;
import top.ainexur.minirpc.transport.ConnectionManager;
import top.ainexur.minirpc.transport.Endpoint;
import top.ainexur.minirpc.transport.TransportClient;
import top.ainexur.minirpc.transport.netty.codec.NettyFrameEncoder;
import top.ainexur.minirpc.transport.netty.codec.NettyFrameSlicer;
import top.ainexur.minirpc.transport.netty.codec.NettyMessageDecoder;
import top.ainexur.minirpc.transport.netty.codec.NettyMessageEncoder;
import top.ainexur.minirpc.transport.netty.handler.ClientResponseHandler;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * 基于 Netty 的客户端实现，支持长连接与请求-响应关联。
 */
public class NettyTransportClient implements TransportClient {
    private final EventLoopGroup group = new NioEventLoopGroup();
    private final MessageCodec codec;
    private final RequestInFlight inflight = new RequestInFlight();
    private final ConnectionManager connectionManager;

    /**
     * 构造默认客户端，使用默认消息编解码器。
     */
    public NettyTransportClient() {
        this(new DefaultMessageCodec());
    }

    /**
     * 构造客户端。
     *
     * @param codec 消息编解码器
     */
    public NettyTransportClient(MessageCodec codec) {
        this.codec = Objects.requireNonNull(codec, "codec");
        Bootstrap bootstrap = new Bootstrap();
        bootstrap.group(group)
                .channel(NioSocketChannel.class)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ChannelPipeline pipeline = ch.pipeline();
                        pipeline.addLast(new NettyFrameSlicer());
                        pipeline.addLast(new NettyMessageDecoder(NettyTransportClient.this.codec));
                        pipeline.addLast(new ClientResponseHandler(inflight));
                        pipeline.addLast(new NettyFrameEncoder());
                        pipeline.addLast(new NettyMessageEncoder(NettyTransportClient.this.codec));
                    }
                });
        this.connectionManager = new SimpleConnectionManager(bootstrap);
    }

    /**
     * 发送请求并返回响应 future。
     *
     * @param endpoint 目标地址
     * @param request  请求对象
     * @return 响应 future
     */
    @Override
    public CompletableFuture<RpcResponse> send(Endpoint endpoint, RpcRequest request) {
        Objects.requireNonNull(endpoint, "endpoint");
        Objects.requireNonNull(request, "request");
        if (request.requestId() <= 0) {
            throw new RpcException(RpcErrorCode.BAD_REQUEST, "requestId must be positive");
        }
        Channel channel = connectionManager.get(endpoint);
        CompletableFuture<RpcResponse> future = new CompletableFuture<>();
        inflight.register(request.requestId(), future);
        channel.writeAndFlush(request).addListener((ChannelFutureListener) f -> {
            if (!f.isSuccess()) {
                inflight.fail(request.requestId(), RpcErrorCode.CONNECTION_CLOSED, "send failed", f.cause());
            }
        });
        return future;
    }

    /**
     * 关闭客户端，释放连接与线程资源。
     */
    @Override
    public void close() {
        connectionManager.close();
        group.shutdownGracefully();
    }
}
