# Đề xuất thiết kế: Hệ thống Real-time Live Polling (Client – Server)

- Ngày: 2026-10-21
- Nhóm: Huỳnh Minh Sang, Nguyễn Hữu Diệu Ly, Nguyễn Thị Hồng Diễm (23DTHC2)
- Môn: Lập trình mạng máy tính — đồ án cuối kỳ
- Thay thế hướng "Mini-Kahoot" trong `Bao_cao_do_an_Mini_Kahoot_Client_Server.docx`

## 1. Mục tiêu và tiêu chí thành công

**Mục tiêu:** xây dựng hệ thống bình chọn trực tiếp (live poll) trong mạng LAN, trọng tâm là **thể hiện kiến thức lập trình mạng**: Socket TCP, UDP broadcast, giao thức tầng ứng dụng tự định nghĩa, xử lý đồng thời nhiều client, phát hiện mất kết nối.

**Thành công khi:**
1. Demo trên ≥ 2 máy thật trong LAN + bot: nhiều người vote, biểu đồ trên Host cập nhật ngay sau mỗi phiếu.
2. Client tự tìm thấy Server bằng UDP, không cần gõ IP.
3. Báo cáo trình bày được đặc tả giao thức và có ảnh Wireshark minh chứng.
4. Bot 50 kết nối vote đồng thời → tổng phiếu đúng bằng 50.

**Ràng buộc / giả định:** Java (JDK 17+), Swing, chỉ thư viện chuẩn, chạy trong LAN lớp học.

## 2. Phạm vi

### Có làm

| Vai trò | Chức năng |
|---|---|
| Host (giáo viên) | Tạo phòng (Server cấp mã 6 số); soạn câu hỏi poll (nội dung + 2–6 lựa chọn); mở/đóng từng câu; xem biểu đồ cột realtime và danh sách người online; xuất kết quả CSV khi kết thúc |
| Client (người tham gia) | Tìm Server trong LAN bằng UDP hoặc nhập IP; nhập mã phòng + tên; vote; đổi phiếu khi poll còn mở; xem kết quả khi poll đóng |
| Server | Nhận kết nối, quản lý nhiều phòng, kiểm tra quyền và dữ liệu, đếm phiếu, broadcast, phát hiện mất kết nối; cửa sổ log để demo |

Mỗi người một phiếu cho mỗi câu; đổi phiếu = ghi đè.

### Không làm (đưa vào "Hướng phát triển")

Tài khoản/đăng nhập, cơ sở dữ liệu, lưu lịch sử, chấm điểm/xếp hạng, TLS/mã hoá, Host kết nối lại, giao diện web.

## 3. Kiến trúc

```
            UDP 5001 (broadcast tìm server)
   ┌──────────────────────────────────────────┐
   │                                          ▼
[Client 1..N] ──TCP 5000──►  [ SERVER ]  ◄──TCP 5000── [Host]
   ▲   JOIN / VOTE            │  RoomManager            ▲ CREATE_ROOM, OPEN/CLOSE_POLL
   └──── POLL, POLL_CLOSED ◄──┘  (Map mã → Room) ──────►┘ JOINED/LEFT, RESULT realtime
```

- Hình sao; Host và Client đều là client TCP, khác nhau ở tập lệnh được phép. Server kiểm tra quyền theo vai trò của kết nối.
- Vai trò kết nối được xác định bởi lệnh đầu tiên hợp lệ: `CREATE_ROOM` → Host của phòng đó; `JOIN` → Client. Một kết nối chỉ có một vai trò.
- Một project Java, 4 package, chạy thành 3 chương trình:
  - `common` — `Protocol` (tên lệnh, escape/tách trường, hằng số cổng)
  - `server` — `LppServer`, `ClientHandler`, `RoomManager`, `Room`, `Poll`, `DiscoveryResponder`, `ServerFrame` (log)
  - `host` — giao diện Host + luồng đọc mạng
  - `client` — giao diện Client + luồng đọc mạng + tìm server UDP
- Công cụ phụ: `BotClient` (kiểm thử tải).

## 4. Giao thức LPP (Live Poll Protocol)

### 4.1 Đóng khung và mã hoá

- Mỗi thông điệp là **một dòng UTF-8 kết thúc bằng `\n`**; đọc bằng `BufferedReader.readLine()`.
- Trường phân tách bằng `|`. Trong nội dung, escape: `\` → `\\`, `|` → `\|`, xuống dòng → `\n`.
- Một dòng tối đa 4 KB; vượt quá → `ERROR|BAD_FORMAT` và đóng kết nối.
- Text thay vì Java Serializable để đọc trực tiếp được trên Wireshark/ncat.

### 4.2 Bảng lệnh

| Hướng | Thông điệp | Phản hồi / tác động |
|---|---|---|
| Host → S | `CREATE_ROOM` | `ROOM_CREATED\|<mã>` |
| Host → S | `OPEN_POLL\|<id>\|<câu hỏi>\|<lc1>\|…\|<lcN>` (2 ≤ N ≤ 6) | Broadcast `POLL\|<id>\|<câu hỏi>\|<lc1>\|…\|<lcN>` cho mọi Client; Host nhận `RESULT` ban đầu toàn 0. Nếu đang có poll mở → tự đóng poll cũ trước |
| Host → S | `CLOSE_POLL\|<id>` | Broadcast `POLL_CLOSED\|<id>\|<n1>\|…\|<nN>` cho Client và Host |
| Host → S | `END_ROOM` | Broadcast `ROOM_ENDED`, xoá phòng, đóng kết nối Client |
| Client → S | `JOIN\|<mã>\|<tên>` | `JOIN_OK\|<mã>`; nếu đang có poll mở thì gửi tiếp `POLL\|…` và `VOTE_OK` (nếu tên đã có phiếu); Host nhận `JOINED\|<tên>` |
| Client → S | `VOTE\|<id>\|<chỉ số 0..N-1>` | `VOTE_OK\|<id>\|<chỉ số>`; Host nhận `RESULT\|<id>\|<n1>\|…\|<nN>` |
| S → Host | `JOINED\|<tên>`, `LEFT\|<tên>` | Cập nhật danh sách |
| Hai chiều | `PING` → `PONG` | Heartbeat |
| S → bất kỳ | `ERROR\|<mã>\|<mô tả>` | Kết nối vẫn giữ (trừ dòng quá dài) |

Mã lỗi: `ROOM_NOT_FOUND`, `NAME_TAKEN`, `NOT_ALLOWED`, `POLL_CLOSED`, `BAD_OPTION`, `BAD_FORMAT`.

### 4.3 Tìm Server (UDP 5001)

Client gửi `DISCOVER_LPP` tới `255.255.255.255:5001`; Server trả unicast `LPP_SERVER|5000|<tên server>`. Client chờ 2 giây, hiển thị danh sách Server tìm được; luôn có ô nhập IP thủ công dự phòng.

### 4.4 Heartbeat

Host/Client gửi `PING` mỗi 5 giây, Server trả `PONG`. Cả hai phía đặt `socket.setSoTimeout(15000)`: 15 giây không nhận được gì → coi như mất kết nối. Không cần luồng hẹn giờ ở Server.

### 4.5 Tình huống đặc biệt

- Client vào khi poll đang mở → nhận ngay poll hiện tại.
- Client rớt rồi vào lại cùng tên → phiếu được giữ (phiếu lưu theo tên). Tên đang online → `NAME_TAKEN`.
- Host rớt → `ROOM_ENDED` cho mọi Client, xoá phòng.
- Client gửi lệnh của Host → `NOT_ALLOWED`.
- `VOTE` cho id không phải poll đang mở → `POLL_CLOSED`; chỉ số ngoài khoảng → `BAD_OPTION`.

### 4.6 Ví dụ một phiên

```
H→S  CREATE_ROOM                       S→H  ROOM_CREATED|482913
C→S  JOIN|482913|Ly                    S→C  JOIN_OK|482913      S→H JOINED|Ly
H→S  OPEN_POLL|1|Ăn trưa ở đâu?|Căng tin|Cơm tấm|Bún bò
                                       S→C  POLL|1|Ăn trưa ở đâu?|Căng tin|Cơm tấm|Bún bò
                                       S→H  RESULT|1|0|0|0
C→S  VOTE|1|2                          S→C  VOTE_OK|1|2         S→H RESULT|1|0|0|1
H→S  CLOSE_POLL|1                      S→all POLL_CLOSED|1|0|0|1
H→S  END_ROOM                          S→all ROOM_ENDED
```

## 5. Xử lý đồng thời

- `LppServer`: luồng chính lặp `accept()`, giao socket cho `ClientHandler` chạy trên `Executors.newCachedThreadPool()`.
- `ClientHandler`: vòng `readLine()` → phân tích → gọi `Room`. `send()` là `synchronized` để không ghi chồng.
- `RoomManager`: `ConcurrentHashMap<String, Room>`; sinh mã 6 số ngẫu nhiên, `putIfAbsent` để không trùng.
- `Room`: mọi thao tác là method `synchronized` → khoá theo từng phòng. Broadcast thực hiện trong khoá để thứ tự `RESULT` đến Host luôn đúng.
  - Giới hạn đã biết: một Client treo làm đầy bộ đệm gửi sẽ làm chậm cả phòng. Nâng cấp: hàng đợi gửi riêng cho mỗi kết nối.
- `Poll`: `Map<tên, chỉ số>`; số phiếu luôn đếm lại từ map → không lệch khi đổi phiếu. Gửi `RESULT` sau mỗi phiếu (đủ cho quy mô lớp; nếu hàng trăm người thì gộp ~200 ms/lần).
- `DiscoveryResponder`: một luồng riêng với `DatagramSocket` cổng 5001.
- Host/Client (Swing): một luồng nền đọc socket; cập nhật giao diện qua `SwingUtilities.invokeLater`; gửi lệnh trực tiếp từ sự kiện nút bấm.

## 6. Xử lý lỗi

- `IOException` / `SocketTimeoutException` ở `ClientHandler` → khối `finally`: rời phòng, báo `LEFT` cho Host (hoặc `ROOM_ENDED` nếu là Host), đóng socket. Các kết nối khác không bị ảnh hưởng.
- Kiểm tra đầu vào tại Server (không tin Client): tên 1–20 ký tự sau khi trim; câu hỏi không rỗng; 2–6 lựa chọn không rỗng; chỉ số trong khoảng; id đúng poll đang mở; lệnh đúng vai trò.
- Cổng bị chiếm khi khởi động Server, Client không kết nối được, Server mất kết nối → hộp thoại báo lỗi, không crash.

## 7. Kiểm thử và demo

- **Self-check** (`main` + `assert`, chạy với `java -ea`): escape/tách trường của `Protocol`; logic `Poll`/`Room` (đổi phiếu, vote khi đóng, sai chỉ số, trùng tên).
- **BotClient**: N kết nối (mặc định 50) join + vote ngẫu nhiên; kiểm tra tổng phiếu = N; ghi thời gian để làm số liệu chịu tải.
- **ncat/telnet thủ công**: gửi `OPEN_POLL` từ kết nối Client → `NOT_ALLOWED`.
- **Wireshark** (`tcp.port==5000 || udp.port==5001`): bắt tay 3 bước; Follow TCP Stream một phiên; gói UDP broadcast + trả lời; FIN/RST khi tắt Client.

**Bảng kiểm thử:**

| # | Trường hợp | Kỳ vọng |
|---|---|---|
| 1 | Client kết nối đúng IP/cổng | Kết nối thành công |
| 2 | Tìm Server bằng UDP | Danh sách hiện Server trong ≤ 2 s |
| 3 | Sai mã phòng | `ROOM_NOT_FOUND`, Client báo lỗi |
| 4 | Trùng tên đang online | `NAME_TAKEN` |
| 5 | Vote | Biểu đồ Host cập nhật ngay |
| 6 | Đổi phiếu | Phiếu cũ −1, phiếu mới +1, tổng không đổi |
| 7 | Vote sau khi đóng poll | `POLL_CLOSED` |
| 8 | Tắt ngang một Client | Sau ≤ 15 s Host nhận `LEFT`, Client khác không ảnh hưởng |
| 9 | Host mất kết nối | Mọi Client nhận `ROOM_ENDED` |
| 10 | Client gửi lệnh Host | `NOT_ALLOWED` |
| 11 | BotClient 50 kết nối | Tổng phiếu = 50 |

**Kịch bản demo:** 2–3 máy LAN thật + BotClient 30 kết nối, chiếu biểu đồ Host.

## 8. Sửa báo cáo (`.docx`) theo đề xuất này

Lỗi đã phát hiện trong bản cũ cần sửa khi viết lại:

1. Đổi đề tài từ quiz chấm điểm sang Live Polling; chốt tên đồ án, điền GVHD.
2. Bỏ bước "Đăng nhập" trong sơ đồ hoạt động và sơ đồ tuần tự (mâu thuẫn với việc chưa có tài khoản).
3. Bỏ CSDL trong sơ đồ kiến trúc và sơ đồ tuần tự (mâu thuẫn với mục 1.3.3).
4. Sơ đồ kiến trúc: vẽ mũi tên hai chiều, thêm kênh UDP discovery.
5. Sơ đồ tuần tự: thông điệp Server chủ động đẩy (`POLL`, `RESULT`) vẽ nét liền, không phải mũi tên phản hồi.
6. Use case: bỏ "bảng xếp hạng/điểm", thống nhất tên chức năng với bảng mô tả.
7. Thay Bảng 2.4 bằng đặc tả giao thức LPP (mục 4); xoá các [CẦN BỔ SUNG] về đóng khung thông điệp.
8. Bổ sung lý thuyết UDP broadcast và heartbeat ở Chương 1.
9. Đánh số bảng liên tục (hiện thiếu Bảng 4.1); thêm hình cho mục 2.5.1 hoặc gộp mục.
10. Xoá ghi chú "chưa có mã nguồn" ở Lời mở đầu; thống nhất phiên bản JDK với tài liệu tham khảo.
11. Chương 4: thay bảng kiểm thử bằng bảng mục 7, thêm số liệu BotClient và ảnh Wireshark.
