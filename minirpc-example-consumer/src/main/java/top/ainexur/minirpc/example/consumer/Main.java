package top.ainexur.minirpc.example.consumer;

import top.ainexur.minirpc.core.consumer.ReferenceFactory;
import top.ainexur.minirpc.example.provider.service.HelloService;
import top.ainexur.minirpc.transport.Endpoint;
import top.ainexur.minirpc.transport.TransportClient;
import top.ainexur.minirpc.transport.netty.NettyTransportClient;

/**
 * Consumer 示例入口。
 */
//TIP To <b>Run</b> code, press <shortcut actionId="Run"/> or
// click the <icon src="AllIcons.Actions.Execute"/> icon in the gutter.
public class Main {
    /**
     * 应用入口。
     *
     * @param args 参数
     */
    public static void main(String[] args) {
        String host = args.length > 0 ? args[0] : "127.0.0.1";
        int port = args.length > 1 ? Integer.parseInt(args[1]) : 8080;

        TransportClient client = new NettyTransportClient();
        try {
            Endpoint endpoint = new Endpoint(host, port);
            ReferenceFactory factory = new ReferenceFactory(client, endpoint);
            HelloService service = factory.getProxy(HelloService.class);
            String result = service.hello("world");
            System.out.println("result=" + result);
        } finally {
            client.close();
        }
    }
}
