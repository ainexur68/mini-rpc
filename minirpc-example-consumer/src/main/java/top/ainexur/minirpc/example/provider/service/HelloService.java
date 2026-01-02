package top.ainexur.minirpc.example.provider.service;

/**
 * 示例服务接口（与 Provider 保持同名同包）。
 */
public interface HelloService {
    /**
     * 打招呼。
     *
     * @param name 名称
     * @return 问候语
     */
    String hello(String name);
}
