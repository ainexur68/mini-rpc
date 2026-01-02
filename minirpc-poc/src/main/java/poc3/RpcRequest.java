package poc3;

/**
 * PoC 请求对象模型。
 */
public class RpcRequest {
    public String service;
    public String method;
    public Object[] args;

    // 无参构造用于 Kryo 反序列化
    public RpcRequest() {
    }

    public RpcRequest(String service, String method, Object[] args) {
        this.service = service;
        this.method = method;
        this.args = args;
    }
}
