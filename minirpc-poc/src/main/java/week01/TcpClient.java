package week01;

import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;

/**
 * PoC：基础 TCP 客户端示例。
 */
public class TcpClient {
    /**
     * 应用入口。
     *
     * @param args 参数
     * @throws IOException IO 异常
     */
    public static void main(String[] args) throws IOException {
        Socket socket = new Socket("127.0.0.1", 8888);
        OutputStream outputStream = socket.getOutputStream();
        outputStream.write("hello".getBytes());
        outputStream.write("world".getBytes());
        outputStream.flush();
    }
}
