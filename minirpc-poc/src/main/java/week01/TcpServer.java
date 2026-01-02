package week01;

import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * PoC：基础 TCP 服务端示例。
 */
public class TcpServer {
    /**
     * 应用入口。
     *
     * @param args 参数
     * @throws IOException IO 异常
     */
    public static void main(String[] args) throws IOException {
        ServerSocket serverSocket = new ServerSocket(8888);
        System.out.println("server started at port 8888");
        Socket accept = serverSocket.accept();
        System.out.println("accept");
        InputStream in = accept.getInputStream();
        byte[] buf = new byte[1024];
        int len = in.read(buf);
        System.out.println("buf len = " + len + "buf = " + new String(buf));
    }
}
