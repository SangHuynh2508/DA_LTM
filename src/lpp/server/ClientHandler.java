package lpp.server;

import lpp.common.Protocol;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.function.Consumer;

/** Phục vụ một kết nối TCP (Host hoặc Client) trên một luồng riêng. Vai trò do lệnh đầu tiên quyết định. */
final class ClientHandler implements Runnable, Peer {
    private enum Role { NONE, HOST, CLIENT }

    private final Socket socket;
    private final BufferedReader in;
    private final Writer out;
    private final RoomManager rooms;
    private final Consumer<String> log;
    private final String who;
    private Role role = Role.NONE;
    private Room room;
    private String name;

    ClientHandler(Socket socket, RoomManager rooms, Consumer<String> log, int timeoutMs) throws IOException {
        this.socket = socket;
        this.rooms = rooms;
        this.log = log;
        this.who = socket.getRemoteSocketAddress().toString();
        socket.setSoTimeout(timeoutMs);
        in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
    }

    @Override public void run() {
        log.accept(who + " kết nối");
        try {
            String line;
            while ((line = Protocol.readLine(in)) != null) {
                if (line.isEmpty()) continue;
                if (!line.equals("PING")) log.accept(who + " → " + line);
                handle(Protocol.decode(line));
            }
        } catch (Protocol.LineTooLongException e) {
            send(Protocol.encode("ERROR", "BAD_FORMAT", "Dòng quá dài"));
        } catch (IOException e) {
            // bên kia đóng kết nối, hoặc quá timeout không nhận được gì (kể cả PING)
        } finally {
            if (role == Role.HOST && room != null) endRoom();
            if (role == Role.CLIENT && room != null) room.leave(name);
            close();
            log.accept(who + " ngắt kết nối");
        }
    }

    private void handle(String[] f) {
        try {
            switch (f[0]) {
                case "PING" -> send("PONG");
                case "CREATE_ROOM" -> {
                    if (role == Role.CLIENT || room != null) { notAllowed(); return; }
                    role = Role.HOST;
                    room = rooms.create(this);
                    send(Protocol.encode("ROOM_CREATED", room.code));
                }
                case "OPEN_POLL" -> {
                    if (!isHost()) { notAllowed(); return; }
                    room.openPoll(Integer.parseInt(f[1]), f[2], Arrays.asList(f).subList(3, f.length));
                }
                case "CLOSE_POLL" -> {
                    if (!isHost()) { notAllowed(); return; }
                    room.closePoll(Integer.parseInt(f[1]));
                }
                case "END_ROOM" -> {
                    if (!isHost()) { notAllowed(); return; }
                    endRoom();
                }
                case "JOIN" -> {
                    if (role != Role.NONE) { notAllowed(); return; }
                    Room r = rooms.get(f[1]);
                    String n = f[2].trim();
                    if (r == null) {
                        send(Protocol.encode("ERROR", "ROOM_NOT_FOUND", "Phòng không tồn tại"));
                        return;
                    }
                    if (r.join(this, n)) {
                        role = Role.CLIENT;
                        room = r;
                        name = n;
                    }
                }
                case "VOTE" -> {
                    if (role != Role.CLIENT) { notAllowed(); return; }
                    room.vote(this, name, Integer.parseInt(f[1]), Integer.parseInt(f[2]));
                }
                default -> send(Protocol.encode("ERROR", "BAD_FORMAT", "Lệnh không hợp lệ: " + f[0]));
            }
        } catch (NumberFormatException | IndexOutOfBoundsException e) {
            send(Protocol.encode("ERROR", "BAD_FORMAT", "Thiếu hoặc sai tham số"));
        }
    }

    private boolean isHost() { return role == Role.HOST && room != null; }

    private void endRoom() {
        room.end();
        rooms.remove(room);
        room = null;
    }

    private void notAllowed() {
        send(Protocol.encode("ERROR", "NOT_ALLOWED", "Không có quyền thực hiện lệnh này"));
    }

    @Override public synchronized void send(String line) {
        try {
            out.write(line);
            out.write('\n');
            out.flush();
        } catch (IOException e) {
            close();
        }
    }

    @Override public void close() {
        try { socket.close(); } catch (IOException ignored) { }
    }
}
