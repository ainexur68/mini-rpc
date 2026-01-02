package top.ainexur.minirpc.transport;

/**
 * 传输端点信息，描述远端的主机与端口。
 */
public record Endpoint(String host, int port) {
}
