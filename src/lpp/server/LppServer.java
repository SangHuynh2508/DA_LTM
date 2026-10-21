package lpp.server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/** Lắng nghe TCP; mỗi kết nối giao cho một ClientHandler chạy trên thread pool. */
public final class LppServer {
    private final RoomManager rooms = new RoomManager();
    private final ExecutorService pool = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "client");
        t.setDaemon(true);
        return t;
    });
    private final Consumer<String> log;
    private final int timeoutMs;
    private ServerSocket server;

    public LppServer(Consumer<String> log, int timeoutMs) {
        this.log = log;
        this.timeoutMs = timeoutMs;
    }

    /** Mở cổng (0 = cổng bất kỳ) và chạy vòng accept trên luồng nền. Ném IOException nếu cổng bị chiếm. */
    public void start(int port) throws IOException {
        server = new ServerSocket(port);
        Thread t = new Thread(this::acceptLoop, "accept");
        t.setDaemon(true);
        t.start();
    }

    public int port() { return server.getLocalPort(); }

    private void acceptLoop() {
        while (!server.isClosed()) {
            try {
                Socket s = server.accept();
                pool.execute(new ClientHandler(s, rooms, log, timeoutMs));
            } catch (IOException e) {
                log.accept("Lỗi accept: " + e.getMessage());
            }
        }
    }
}
