package lpp.server;

import java.util.ArrayList;
import java.util.List;

public class RoomTest {
    static final class FakePeer implements Peer {
        final List<String> got = new ArrayList<>();
        boolean closed;
        public void send(String line) { got.add(line); }
        public void close() { closed = true; }
        String last() { return got.get(got.size() - 1); }
    }

    public static void main(String[] args) {
        FakePeer host = new FakePeer(), ly = new FakePeer(), diem = new FakePeer();
        Room room = new Room("123456", host);

        // vào phòng, trùng tên, tên không hợp lệ
        check(room.join(ly, "Ly"), "Ly vào phòng");
        check(ly.last().equals("JOIN_OK|123456"), "Ly nhận JOIN_OK");
        check(host.last().equals("JOINED|Ly"), "Host nhận JOINED");
        FakePeer dup = new FakePeer();
        check(!room.join(dup, "Ly") && dup.last().startsWith("ERROR|NAME_TAKEN"), "trùng tên");
        FakePeer blank = new FakePeer();
        check(!room.join(blank, "") && blank.last().startsWith("ERROR|BAD_FORMAT"), "tên rỗng");
        FakePeer longName = new FakePeer();
        check(!room.join(longName, "x".repeat(21)) && longName.last().startsWith("ERROR|BAD_FORMAT"), "tên > 20 ký tự");

        // mở poll: 7 lựa chọn bị từ chối, 3 lựa chọn hợp lệ
        room.openPoll(1, "Q", List.of("1", "2", "3", "4", "5", "6", "7"));
        check(host.last().startsWith("ERROR|BAD_FORMAT"), "7 lựa chọn bị từ chối");
        room.openPoll(1, "Q", List.of("a", " "));
        check(host.last().startsWith("ERROR|BAD_FORMAT"), "lựa chọn rỗng bị từ chối");
        room.openPoll(1, "Ăn trưa?", List.of("Căng tin", "Cơm tấm", "Bún bò"));
        check(ly.last().equals("POLL|1|Ăn trưa?|Căng tin|Cơm tấm|Bún bò"), "Client nhận POLL");
        check(host.last().equals("RESULT|1|0|0|0"), "Host nhận RESULT ban đầu");

        // vote, đổi phiếu, sai chỉ số, sai id
        room.vote(ly, "Ly", 1, 2);
        check(ly.last().equals("VOTE_OK|1|2") && host.last().equals("RESULT|1|0|0|1"), "vote");
        room.vote(ly, "Ly", 1, 0);
        check(host.last().equals("RESULT|1|1|0|0"), "đổi phiếu không được đếm trùng");
        room.vote(ly, "Ly", 1, 3);
        check(ly.last().startsWith("ERROR|BAD_OPTION"), "chỉ số ngoài khoảng");
        room.vote(ly, "Ly", 9, 0);
        check(ly.last().startsWith("ERROR|POLL_CLOSED"), "id không phải poll đang mở");

        // vào giữa chừng nhận poll hiện tại; rớt rồi vào lại vẫn giữ phiếu
        check(room.join(diem, "Diem"), "Diem vào phòng");
        check(diem.last().equals("POLL|1|Ăn trưa?|Căng tin|Cơm tấm|Bún bò"), "vào giữa chừng nhận POLL");
        room.leave("Ly");
        check(host.last().equals("LEFT|Ly"), "Host nhận LEFT");
        FakePeer ly2 = new FakePeer();
        check(room.join(ly2, "Ly"), "Ly vào lại");
        check(ly2.last().equals("VOTE_OK|1|0"), "vào lại nhận phiếu cũ");

        // đóng poll, vote sau khi đóng, đóng lần hai
        room.closePoll(1);
        check(diem.last().equals("POLL_CLOSED|1|1|0|0") && host.last().equals("POLL_CLOSED|1|1|0|0"), "POLL_CLOSED");
        room.vote(diem, "Diem", 1, 1);
        check(diem.last().startsWith("ERROR|POLL_CLOSED"), "vote sau khi đóng");
        room.closePoll(1);
        check(host.last().startsWith("ERROR|POLL_CLOSED"), "đóng poll đã đóng");

        // mở poll mới khi poll cũ còn mở → poll cũ tự đóng
        room.openPoll(2, "A?", List.of("x", "y"));
        room.openPoll(3, "B?", List.of("x", "y"));
        check(diem.got.contains("POLL_CLOSED|2|0|0"), "poll cũ tự đóng");

        // kết thúc phòng
        room.end();
        check(diem.last().equals("ROOM_ENDED") && diem.closed, "Client nhận ROOM_ENDED và bị đóng");
        check(host.last().equals("ROOM_ENDED"), "Host nhận ROOM_ENDED");
        FakePeer late = new FakePeer();
        check(!room.join(late, "Late") && late.last().startsWith("ERROR|ROOM_NOT_FOUND"), "vào phòng đã kết thúc");
        room.end();

        System.out.println("RoomTest OK");
    }

    static void check(boolean ok, String what) { if (!ok) throw new AssertionError("FAIL: " + what); }
}
