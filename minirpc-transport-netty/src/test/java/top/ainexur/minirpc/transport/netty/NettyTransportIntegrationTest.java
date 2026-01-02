package top.ainexur.minirpc.transport.netty;

import org.junit.jupiter.api.Test;
import top.ainexur.minirpc.common.RpcErrorCode;
import top.ainexur.minirpc.protocol.message.RpcRequest;
import top.ainexur.minirpc.protocol.message.RpcResponse;
import top.ainexur.minirpc.transport.Endpoint;
import top.ainexur.minirpc.transport.RequestHandler;
import top.ainexur.minirpc.transport.TransportClient;
import top.ainexur.minirpc.transport.TransportServer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 传输层集成测试，验证请求-响应、并发与线程模型。
 */
class NettyTransportIntegrationTest {
    @Test
    void requestResponseRoundTrip() throws Exception {
        RequestHandler handler = request -> new RpcResponse(
                request.requestId(),
                RpcErrorCode.OK.code,
                "Hello " + request.args()[0],
                Map.of()
        );
        TransportServer server = new NettyTransportServer(handler);
        server.start(0);
        TransportClient client = new NettyTransportClient();
        try {
            Endpoint endpoint = new Endpoint("127.0.0.1", server.port());
            RpcRequest request = buildRequest(1L, "x");
            RpcResponse response = client.send(endpoint, request).get(3, TimeUnit.SECONDS);
            assertNotNull(response);
            assertEquals(RpcErrorCode.OK.code, response.code());
            assertEquals("Hello x", response.returnValue());
        } finally {
            client.close();
            server.stop();
        }
    }

    @Test
    void concurrentRequestsComplete() throws Exception {
        RequestHandler handler = request -> new RpcResponse(
                request.requestId(),
                RpcErrorCode.OK.code,
                request.args()[0],
                Map.of()
        );
        TransportServer server = new NettyTransportServer(handler);
        server.start(0);
        TransportClient client = new NettyTransportClient();
        try {
            Endpoint endpoint = new Endpoint("127.0.0.1", server.port());
            int total = 1000;
            List<CompletableFuture<RpcResponse>> futures = new ArrayList<>(total);
            for (int i = 0; i < total; i++) {
                RpcRequest request = buildRequest(i + 1L, "v" + i);
                futures.add(client.send(endpoint, request));
            }
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                    .get(5, TimeUnit.SECONDS);
            for (CompletableFuture<RpcResponse> future : futures) {
                RpcResponse response = future.get(1, TimeUnit.SECONDS);
                assertEquals(RpcErrorCode.OK.code, response.code());
            }
        } finally {
            client.close();
            server.stop();
        }
    }

    @Test
    void handlerRunsOnVirtualThread() throws Exception {
        AtomicReference<Boolean> threadIsVirtual = new AtomicReference<>();
        RequestHandler handler = request -> {
            threadIsVirtual.compareAndSet(null, Thread.currentThread().isVirtual());
            return new RpcResponse(request.requestId(), RpcErrorCode.OK.code, "ok", Map.of());
        };
        TransportServer server = new NettyTransportServer(handler);
        server.start(0);
        TransportClient client = new NettyTransportClient();
        try {
            Endpoint endpoint = new Endpoint("127.0.0.1", server.port());
            RpcRequest request = buildRequest(99L, "vt");
            RpcResponse response = client.send(endpoint, request).get(3, TimeUnit.SECONDS);
            assertEquals(RpcErrorCode.OK.code, response.code());
            Boolean isVirtual = threadIsVirtual.get();
            assertNotNull(isVirtual);
            assertTrue(isVirtual);
        } finally {
            client.close();
            server.stop();
        }
    }

    private static RpcRequest buildRequest(long requestId, String arg) {
        return new RpcRequest(
                requestId,
                "HelloService",
                "hello",
                new String[]{"java.lang.String"},
                new Object[]{arg},
                Map.of()
        );
    }
}
