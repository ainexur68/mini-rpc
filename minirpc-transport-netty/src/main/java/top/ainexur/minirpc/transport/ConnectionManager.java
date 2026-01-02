package top.ainexur.minirpc.transport;

import io.netty.channel.Channel;

/**
 * 连接管理器，负责按端点维度复用与获取连接。
 */
public interface ConnectionManager {
    /**
     * 获取或创建指定端点的连接。
     *
     * @param endpoint 目标地址
     * @return 可用的连接
     */
    Channel get(Endpoint endpoint);

    /**
     * 关闭所有连接并释放资源。
     */
    void close();
}
