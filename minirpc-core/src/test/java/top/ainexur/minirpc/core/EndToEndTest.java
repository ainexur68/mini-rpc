package top.ainexur.minirpc.core;

import org.junit.jupiter.api.Test;
import top.ainexur.minirpc.common.RpcErrorCode;
import top.ainexur.minirpc.common.RpcException;
import top.ainexur.minirpc.core.consumer.ReferenceFactory;
import top.ainexur.minirpc.core.provider.ProviderDispatcher;
import top.ainexur.minirpc.core.provider.ServiceExporter;
import top.ainexur.minirpc.transport.Endpoint;
import top.ainexur.minirpc.transport.TransportClient;
import top.ainexur.minirpc.transport.TransportServer;
import top.ainexur.minirpc.transport.netty.NettyTransportClient;
import top.ainexur.minirpc.transport.netty.NettyTransportServer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * E1.4 端到端测试：代理调用 -> 传输 -> 分发 -> 返回结果。
 */
class EndToEndTest {
    @Test
    void helloServiceRoundTrip() {
        ServiceExporter exporter = new ServiceExporter();
        exporter.register(HelloService.class, new HelloServiceImpl());
        ProviderDispatcher dispatcher = new ProviderDispatcher(exporter);

        TransportServer server = new NettyTransportServer(dispatcher::dispatch);
        server.start(0);
        TransportClient client = new NettyTransportClient();
        try {
            Endpoint endpoint = new Endpoint("127.0.0.1", server.port());
            ReferenceFactory factory = new ReferenceFactory(client, endpoint);
            HelloService helloService = factory.getProxy(HelloService.class);
            String result = helloService.hello("x");
            assertEquals("Hello x", result);
        } finally {
            client.close();
            server.stop();
        }
    }

    @Test
    void errorResponseTranslated() {
        ServiceExporter exporter = new ServiceExporter();
        exporter.register(HelloService.class, new HelloServiceImpl());
        ProviderDispatcher dispatcher = new ProviderDispatcher(exporter);

        TransportServer server = new NettyTransportServer(dispatcher::dispatch);
        server.start(0);
        TransportClient client = new NettyTransportClient();
        try {
            Endpoint endpoint = new Endpoint("127.0.0.1", server.port());
            ReferenceFactory factory = new ReferenceFactory(client, endpoint);
            HelloService helloService = factory.getProxy(HelloService.class);
            RpcException ex = assertThrows(RpcException.class, helloService::fail);
            assertEquals(RpcErrorCode.SERVER_ERROR, ex.code());
        } finally {
            client.close();
            server.stop();
        }
    }

    public interface HelloService {
        String hello(String name);

        String fail();
    }

    public static class HelloServiceImpl implements HelloService {
        @Override
        public String hello(String name) {
            return "Hello " + name;
        }

        @Override
        public String fail() {
            throw new IllegalStateException("boom");
        }
    }
}
