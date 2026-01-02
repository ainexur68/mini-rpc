package top.ainexur.minirpc.example.provider;

import top.ainexur.minirpc.core.provider.ProviderDispatcher;
import top.ainexur.minirpc.core.provider.ServiceExporter;
import top.ainexur.minirpc.example.provider.service.HelloService;
import top.ainexur.minirpc.example.provider.service.HelloServiceImpl;
import top.ainexur.minirpc.transport.TransportServer;
import top.ainexur.minirpc.transport.netty.NettyTransportServer;

/**
 * Provider 示例入口。
 */
//TIP To <b>Run</b> code, press <shortcut actionId="Run"/> or
// click the <icon src="AllIcons.Actions.Execute"/> icon in the gutter.
public class Main {
    /**
     * 应用入口。
     *
     * @param args 参数
     */
    public static void main(String[] args) throws InterruptedException {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 8080;

        ServiceExporter exporter = new ServiceExporter();
        exporter.register(HelloService.class, new HelloServiceImpl());
        ProviderDispatcher dispatcher = new ProviderDispatcher(exporter);

        TransportServer server = new NettyTransportServer(dispatcher::dispatch);
        server.start(port);
        System.out.println("MiniRPC provider started on port " + server.port());

        Runtime.getRuntime().addShutdownHook(new Thread(server::stop));
        Thread.currentThread().join();
    }
}
