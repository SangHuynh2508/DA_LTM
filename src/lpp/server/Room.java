package lpp.server;

import lpp.common.Protocol;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Một phòng poll. Mọi method public đều synchronized → khoá theo từng phòng.
 * Broadcast thực hiện trong khoá để thứ tự RESULT đến Host luôn đúng.
 * ponytail: một Client không đọc socket làm cả phòng chờ tới khi watchdog của ClientHandler cắt nó (timeout/3 = 5 s);
 * nâng cấp = hàng đợi gửi riêng mỗi kết nối.
 */
final class Room {
    static final int MAX_NAME = 20, MIN_OPTIONS = 2, MAX_OPTIONS = 6;

    final String code;
    private final Peer host;
    private final Map<String, Peer> members = new LinkedHashMap<>();
    private Poll poll;
    private boolean ended;

    Room(String code, Peer host) {
        this.code = code;
        this.host = host;
    }

    /** @return true nếu vào phòng thành công (đã gửi JOIN_OK); false nếu đã gửi ERROR. */
    synchronized boolean join(Peer p, String name) {
        if (ended) return error(p, "ROOM_NOT_FOUND", "Phòng không tồn tại");
        if (name.isEmpty() || name.length() > MAX_NAME) return error(p, "BAD_FORMAT", "Tên phải dài 1-20 ký tự");
        if (members.containsKey(name)) return error(p, "NAME_TAKEN", "Tên đã có người dùng");
        members.put(name, p);
        p.send(Protocol.encode("JOIN_OK", code));
        host.send(Protocol.encode("JOINED", name));
        if (poll != null && poll.open) {
            p.send(pollLine());
            Integer v = poll.voteOf(name);
            if (v != null) p.send(Protocol.encode("VOTE_OK", id(), String.valueOf(v)));
        }
        return true;
    }

    synchronized void leave(String name) {
        if (members.remove(name) != null) host.send(Protocol.encode("LEFT", name));
    }

    synchronized void vote(Peer p, String name, int pollId, int option) {
        if (ended || poll == null || !poll.open || poll.id != pollId) {
            error(p, "POLL_CLOSED", "Câu hỏi đã đóng");
            return;
        }
        if (option < 0 || option >= poll.options.size()) {
            error(p, "BAD_OPTION", "Lựa chọn không hợp lệ");
            return;
        }
        poll.vote(name, option);
        p.send(Protocol.encode("VOTE_OK", id(), String.valueOf(option)));
        host.send(resultLine("RESULT"));
    }

    synchronized void openPoll(int pollId, String question, List<String> options) {
        if (question.isBlank() || options.size() < MIN_OPTIONS || options.size() > MAX_OPTIONS
                || options.stream().anyMatch(String::isBlank)) {
            error(host, "BAD_FORMAT", "Câu hỏi cần nội dung và 2-6 lựa chọn không rỗng");
            return;
        }
        if (poll != null && poll.open) closeCurrent();
        poll = new Poll(pollId, question, options);
        broadcast(pollLine());
        host.send(resultLine("RESULT"));
    }

    synchronized void closePoll(int pollId) {
        if (poll == null || !poll.open || poll.id != pollId) {
            error(host, "POLL_CLOSED", "Không có câu hỏi đang mở với id này");
            return;
        }
        closeCurrent();
    }

    /** Gửi ROOM_ENDED cho mọi người và đóng kết nối Client. Gọi nhiều lần không sao. */
    synchronized void end() {
        if (ended) return;
        ended = true;
        String line = Protocol.encode("ROOM_ENDED");
        for (Peer p : members.values()) {
            p.send(line);
            p.close();
        }
        members.clear();
        host.send(line);
    }

    private void closeCurrent() {
        poll.open = false;
        String line = resultLine("POLL_CLOSED");
        broadcast(line);
        host.send(line);
    }

    private void broadcast(String line) {
        for (Peer p : members.values()) p.send(line);
    }

    private String id() { return String.valueOf(poll.id); }

    private String pollLine() {
        List<String> f = new ArrayList<>(List.of("POLL", id(), poll.question));
        f.addAll(poll.options);
        return Protocol.encode(f.toArray(new String[0]));
    }

    private String resultLine(String cmd) {
        int[] c = poll.counts();
        String[] f = new String[c.length + 2];
        f[0] = cmd;
        f[1] = id();
        for (int i = 0; i < c.length; i++) f[i + 2] = String.valueOf(c[i]);
        return Protocol.encode(f);
    }

    private static boolean error(Peer p, String code, String msg) {
        p.send(Protocol.encode("ERROR", code, msg));
        return false;
    }
}
