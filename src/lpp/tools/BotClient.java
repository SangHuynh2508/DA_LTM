package lpp.tools;

import lpp.common.LineConnection;
import lpp.common.Protocol;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * test <host> <n>         : tự tạo phòng, cho n bot vote một câu, kiểm tra tổng phiếu = n, in thời gian.
 * join <host> <mã> <n>    : n bot vào phòng có sẵn, tự vote ngẫu nhiên mỗi khi có câu hỏi (dùng khi demo).
 */
public final class BotClient {
    public static void main(String[] args) throws Exception {
        if (args.length == 3 && args[0].equals("test")) {
            test(args[1], Integer.parseInt(args[2]));
        } else if (args.length == 4 && args[0].equals("join")) {
            joinBots(args[1], args[2], Integer.parseInt(args[3]));
            System.out.println("Đã cho " + args[3] + " bot vào phòng " + args[2] + ". Ctrl+C để thoát.");
            Thread.currentThread().join();
        } else {
            System.out.println("Cách dùng:\n  BotClient test <host> <n>\n  BotClient join <host> <mã phòng> <n>");
            System.exit(2);
        }
    }

    static List<LineConnection> joinBots(String host, String room, int n) throws IOException {
        List<LineConnection> bots = new ArrayList<>();
        for (int i = 1; i <= n; i++) {
            LineConnection c = new LineConnection(host, Protocol.TCP_PORT);
            c.start(f -> {
                if (f[0].equals("POLL")) {
                    c.send("VOTE", f[1], String.valueOf(ThreadLocalRandom.current().nextInt(f.length - 3)));
                } else if (f[0].equals("ERROR")) {
                    System.out.println("Bot lỗi: " + String.join("|", f));
                }
            }, () -> { });
            c.send("JOIN", room, "Bot" + i);
            bots.add(c);
        }
        return bots;
    }

    static void test(String host, int n) throws Exception {
        BlockingQueue<String[]> inbox = new LinkedBlockingQueue<>();
        LineConnection h = new LineConnection(host, Protocol.TCP_PORT);
        h.start(inbox::add, () -> { });
        h.send("CREATE_ROOM");
        String room = waitFor(inbox, "ROOM_CREATED")[1];

        joinBots(host, room, n);
        for (int joined = 0; joined < n; joined++) waitFor(inbox, "JOINED");

        long t0 = System.nanoTime();
        h.send("OPEN_POLL", "1", "Kiểm thử tải", "A", "B", "C", "D");
        int total = 0;
        while (total < n) {
            String[] r = waitFor(inbox, "RESULT");
            total = 0;
            for (int i = 2; i < r.length; i++) total += Integer.parseInt(r[i]);
        }
        long ms = (System.nanoTime() - t0) / 1_000_000;
        h.send("END_ROOM");
        System.out.printf("PASS: %d bot, tổng phiếu = %d, %d ms từ OPEN_POLL tới phiếu cuối%n", n, total, ms);
        System.exit(0);
    }

    /** Lấy thông điệp kế tiếp có lệnh cmd (bỏ qua lệnh khác); ERROR hoặc quá 10 s → FAIL. */
    private static String[] waitFor(BlockingQueue<String[]> inbox, String cmd) throws InterruptedException {
        while (true) {
            String[] f = inbox.poll(10, TimeUnit.SECONDS);
            if (f == null) { System.out.println("FAIL: quá 10 s không nhận được " + cmd); System.exit(1); }
            if (f[0].equals(cmd)) return f;
            if (f[0].equals("ERROR")) { System.out.println("FAIL: " + String.join("|", f)); System.exit(1); }
        }
    }
}
