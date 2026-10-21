package lpp.server;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/** Danh sách phòng đang mở, truy cập đồng thời từ nhiều ClientHandler. */
final class RoomManager {
    private final ConcurrentHashMap<String, Room> rooms = new ConcurrentHashMap<>();

    Room create(Peer host) {
        while (true) {
            String code = String.format("%06d", ThreadLocalRandom.current().nextInt(1_000_000));
            Room room = new Room(code, host);
            if (rooms.putIfAbsent(code, room) == null) return room;
        }
    }

    Room get(String code) { return rooms.get(code); }

    void remove(Room room) { rooms.remove(room.code, room); }
}
