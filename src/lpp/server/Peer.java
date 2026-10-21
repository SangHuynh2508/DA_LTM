package lpp.server;

/** Một đầu kết nối mà Room gửi thông điệp tới (ClientHandler; trong test là bản giả). */
interface Peer {
    void send(String line);
    void close();
}
