package top.ainexur.minirpc.transport.netty;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import top.ainexur.minirpc.common.RpcErrorCode;
import top.ainexur.minirpc.common.RpcException;
import top.ainexur.minirpc.transport.ConnectionManager;
import top.ainexur.minirpc.transport.Endpoint;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 基于 Netty 的简单连接管理器，按 Endpoint 维度复用连接。
 */
public class SimpleConnectionManager implements ConnectionManager {
    private final Bootstrap bootstrap;
    private final ConcurrentHashMap<Endpoint, Channel> channels = new ConcurrentHashMap<>();

    /**
     * 构造连接管理器。
     *
     * @param bootstrap Netty Bootstrap
     */
    public SimpleConnectionManager(Bootstrap bootstrap) {
        this.bootstrap = bootstrap;
    }

    /**
     * 获取可用连接，不存在或不可用时创建新连接。
     *
     * @param endpoint 目标地址
     * @return 可用连接
     */
    @Override
    public Channel get(Endpoint endpoint) {
        Channel cached = channels.get(endpoint);
        if (cached != null && cached.isActive()) {
            return cached;
        }
        Channel channel = connect(endpoint);
        channels.put(endpoint, channel);
        return channel;
    }

    /**
     * 关闭所有连接。
     */
    @Override
    public void close() {
        for (Channel channel : channels.values()) {
            channel.close();
        }
        channels.clear();
    }

    /**
     * 建立新的连接。
     *
     * @param endpoint 目标地址
     * @return 新建连接
     */
    private Channel connect(Endpoint endpoint) {
        ChannelFuture future = bootstrap.connect(endpoint.host(), endpoint.port()).syncUninterruptibly();
        if (!future.isSuccess()) {
            throw new RpcException(RpcErrorCode.CONNECTION_CLOSED, "connect failed", future.cause());
        }
        return future.channel();
    }
}
