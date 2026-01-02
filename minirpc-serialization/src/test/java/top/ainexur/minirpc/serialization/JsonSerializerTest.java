package top.ainexur.minirpc.serialization;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class JsonSerializerTest {
    @Test
    void roundTripRequest() {
        JsonSerializer serializer = new JsonSerializer();
        TestRequest req = new TestRequest("HelloService", "hello", new String[]{"x"});
        byte[] bytes = serializer.serialize(req);
        TestRequest back = serializer.deserialize(bytes, TestRequest.class);
        assertNotNull(back);
        assertEquals(req, back);
    }

    @Test
    void roundTripResponse() {
        JsonSerializer serializer = new JsonSerializer();
        TestResponse resp = new TestResponse(1L, 0, "OK");
        byte[] bytes = serializer.serialize(resp);
        TestResponse back = serializer.deserialize(bytes, TestResponse.class);
        assertNotNull(back);
        assertEquals(resp, back);
    }

    private record TestRequest(String service, String method, String[] args) {}

    private record TestResponse(long requestId, int code, String message) {}
}
