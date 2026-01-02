package top.ainexur.minirpc.example.provider.service;

/**
 * 示例服务实现。
 */
public class HelloServiceImpl implements HelloService {
    @Override
    public String hello(String name) {
        return "Hello " + name;
    }
}
