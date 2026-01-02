package poc3;

/**
 * PoC：另一种请求对象模型。
 */
public class OtherRequest {
    public long id;
    public String payload;

    public OtherRequest() {
    }

    public OtherRequest(long id, String payload) {
        this.id = id;
        this.payload = payload;
    }
}
