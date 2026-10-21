package lpp.server;

import lpp.common.Protocol;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/** Test qua socket thật, gửi/nhận từng dòng giống telnet. */
public class ServerTest {
    static final class Raw implements Closeable {
        final Socket s;
        final BufferedReader in;
        final Writer out;

        Raw(int port) throws IOException {
            s = new Socket("localhost", port);
            s.setSoTimeout(3000);
            in = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
            out = new OutputStreamWriter(s.getOutputStream(), StandardCharsets.UTF_8);
        }

        Raw send(String line) throws IOException {
            out.write(line + "\n");
            out.flush();
            return this;
        }

        String read() throws IOException { return in.readLine(); }

        public void close() throws IOException { s.close(); }
    }

    public static void main(String[] args) throws Exception {
        mainFlow();
        heartbeatAndHostDrop();
        stuckClientDoesNotFreezeRoom();
        System.out.println("ServerTest OK");
    }

    /** Client vẫn gửi PING nhưng không bao giờ đọc → bộ đệm gửi đầy; phòng không được đóng băng. */
    static void stuckClientDoesNotFreezeRoom() throws Exception {
        LppServer server = new LppServer(msg -> { }, 300);
        server.start(0);
        try (Raw host = new Raw(server.port()); Raw stuck = new Raw(server.port())) {
            String code = Protocol.decode(host.send("CREATE_ROOM").read())[1];
            stuck.send("JOIN|" + code + "|Treo");
            host.read(); // JOINED|Treo

            Thread pinger = new Thread(() -> {
                try { while (true) { stuck.send("PING"); Thread.sleep(50); } } catch (Exception ignored) { }
            });
            Thread drainHost = new Thread(() -> {
                try { while (host.read() != null) { } } catch (IOException ignored) { }
            });
            String big = "x".repeat(4000);
            Thread flood = new Thread(() -> {
                // gửi liên tục để chính Host không bị timeout 300 ms
                try { for (int i = 1; ; i++) host.send("OPEN_POLL|" + i + "|" + big + "|a|b"); } catch (IOException ignored) { }
            });
            for (Thread t : new Thread[]{pinger, drainHost, flood}) { t.setDaemon(true); t.start(); }
            Thread.sleep(1500);

            try (Raw fresh = new Raw(server.port())) {
                String got = fresh.send("JOIN|" + code + "|Moi").read();
                check(("JOIN_OK|" + code).equals(got),
                        "phòng vẫn nhận người mới khi có một Client không đọc socket, nhận: " + got);
            }
        }
    }

    static void mainFlow() throws Exception {
        LppServer server = new LppServer(msg -> { }, 5000);
        server.start(0);
        try (Raw host = new Raw(server.port()); Raw ly = new Raw(server.port()); Raw evil = new Raw(server.port())) {
            String code = Protocol.decode(host.send("CREATE_ROOM").read())[1];
            check(code.matches("\\d{6}"), "mã phòng 6 số: " + code);

            check(ly.send("JOIN|999999x|Ly").read().startsWith("ERROR|ROOM_NOT_FOUND"), "sai mã phòng");
            check(ly.send("JOIN|" + code + "|Ly").read().equals("JOIN_OK|" + code), "join");
            check(host.read().equals("JOINED|Ly"), "Host nhận JOINED");

            String q = "Ăn trưa | ở đâu?";
            host.send(Protocol.encode("OPEN_POLL", "1", q, "Căng tin", "Bún bò"));
            check(Protocol.decode(ly.read())[2].equals(q), "câu hỏi tiếng Việt + '|' đi qua mạng nguyên vẹn");
            check(host.read().equals("RESULT|1|0|0"), "RESULT ban đầu");

            check(ly.send("OPEN_POLL|2|x|a|b").read().startsWith("ERROR|NOT_ALLOWED"), "Client không được gửi lệnh Host");
            check(ly.send("CREATE_ROOM").read().startsWith("ERROR|NOT_ALLOWED"), "Client không được tạo phòng");
            check(ly.send("VOTE|1|abc").read().startsWith("ERROR|BAD_FORMAT"), "sai tham số");
            check(ly.send("VOTE").read().startsWith("ERROR|BAD_FORMAT"), "thiếu tham số");
            check(ly.send("HELLO").read().startsWith("ERROR|BAD_FORMAT"), "lệnh lạ");
            check(ly.send("PING").read().equals("PONG"), "vẫn giữ kết nối sau lỗi");

            ly.out.write("VOTE|1|1\r\n");
            ly.out.flush();
            check(ly.read().equals("VOTE_OK|1|1"), "vote kết thúc bằng \\r\\n (telnet)");
            check(host.read().equals("RESULT|1|0|1"), "RESULT realtime");

            // gửi đúng MAX_LINE+1 ký tự, không kèm '\n', để server không còn dữ liệu chưa đọc khi đóng (tránh RST)
            evil.out.write("x".repeat(Protocol.MAX_LINE + 1));
            evil.out.flush();
            check(evil.read().startsWith("ERROR|BAD_FORMAT"), "dòng quá dài");
            check(evil.read() == null, "server đóng kết nối gửi dòng quá dài");

            host.send("END_ROOM");
            check(ly.read().equals("ROOM_ENDED"), "Client nhận ROOM_ENDED");
            check(ly.read() == null, "server đóng kết nối Client khi kết thúc phòng");
            check(host.read().equals("ROOM_ENDED"), "Host nhận ROOM_ENDED");
            check(host.send("PING").read().equals("PONG"), "Host vẫn kết nối sau END_ROOM");
        }
    }

    static void heartbeatAndHostDrop() throws Exception {
        LppServer server = new LppServer(msg -> { }, 300);
        server.start(0);
        try (Raw host = new Raw(server.port()); Raw quiet = new Raw(server.port())) {
            String code = Protocol.decode(host.send("CREATE_ROOM").read())[1];
            quiet.send("JOIN|" + code + "|Im").read();
            host.read(); // JOINED|Im

            // Host ping đều, Client im lặng → sau 300 ms server coi Client đã chết
            String got;
            do {
                Thread.sleep(50);
                got = host.send("PING").read();
            } while (got.equals("PONG"));
            check(got.equals("LEFT|Im"), "Client im lặng quá timeout → Host nhận LEFT, nhận được: " + got);
            check(quiet.read() == null, "server đã đóng kết nối Client im lặng");

            try (Raw c2 = new Raw(server.port())) {
                c2.send("JOIN|" + code + "|Hai").read();
                host.close();
                check(c2.read().equals("ROOM_ENDED"), "Host mất kết nối → Client nhận ROOM_ENDED");
            }
        }
    }

    static void check(boolean ok, String what) { if (!ok) throw new AssertionError("FAIL: " + what); }
}
