package top.ainexur.minirpc.transport;

/**
 * 服务端传输接口，负责监听端口并接收请求。
 */
public interface TransportServer {
    /**
     * 启动服务并绑定端口。
     *
     * @param port 监听端口，0 表示随机端口
     */
    void start(int port);

    /**
     * 停止服务并释放资源。
     */
    void stop();

    /**
     * 获取实际绑定端口。
     *
     * @return 监听端口
     */
    int port();
}
