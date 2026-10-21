package lpp.common;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/** Kết nối TCP phía Host/Client: luồng nền đọc từng dòng, tự gửi PING mỗi 5 s, timeout đọc 15 s. */
public final class LineConnection implements Closeable {
    private final Socket socket = new Socket();
    private final BufferedReader in;
    private final Writer out;
    private final ScheduledExecutorService pinger = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "ping");
        t.setDaemon(true);
        return t;
    });

    public LineConnection(String host, int port) throws IOException {
        try {
            socket.connect(new InetSocketAddress(host, port), 3000);
            socket.setSoTimeout(Protocol.TIMEOUT_MS);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            close();
            throw e;
        }
    }

    /** onMessage/onClosed chạy trên luồng nền — giao diện Swing phải tự SwingUtilities.invokeLater. */
    public void start(Consumer<String[]> onMessage, Runnable onClosed) {
        Thread reader = new Thread(() -> {
            try {
                String line;
                while ((line = Protocol.readLine(in)) != null) {
                    if (line.isEmpty() || line.equals("PONG")) continue;
                    onMessage.accept(Protocol.decode(line));
                }
            } catch (IOException ignored) {
                // server đóng, mất mạng, hoặc quá timeout
            } finally {
                close();
                onClosed.run();
            }
        }, "reader");
        reader.setDaemon(true);
        reader.start();
        pinger.scheduleAtFixedRate(() -> send("PING"), Protocol.PING_MS, Protocol.PING_MS, TimeUnit.MILLISECONDS);
    }

    public synchronized void send(String... fields) {
        try {
            out.write(Protocol.encode(fields));
            out.write('\n');
            out.flush();
        } catch (IOException e) {
            close();
        }
    }

    @Override public void close() {
        pinger.shutdownNow();
        try { socket.close(); } catch (IOException ignored) { }
    }
}
