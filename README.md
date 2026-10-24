# LPP – Real-time Live Polling

Đồ án Lập trình mạng: Server / Host / Client giao tiếp bằng giao thức text LPP qua TCP 5000, tìm server bằng UDP 5001.
Đặc tả: `docs/superpowers/specs/2026-10-21-live-polling-design.md`.

## Mở bằng NetBeans

File → Open Project → chọn thư mục `DA_LTM` (NetBeans nhận ra `pom.xml`, Maven có sẵn trong NetBeans).
- **Run Project** (F6): chạy Server.
- Host / Client / BotClient / các test: mở file trong `src` hoặc `test`, chuột phải → **Run File** (Shift+F6).
  BotClient cần tham số: chuột phải project → Properties → Run, hoặc dùng lệnh ở bảng dưới.

## Build bằng dòng lệnh (JDK 17+)

Git Bash:
```bash
javac --release 17 -encoding UTF-8 -d out $(find src test -name '*.java')
```
PowerShell:
```powershell
javac --release 17 -encoding UTF-8 -d out (Get-ChildItem -Recurse -Filter *.java src,test).FullName
```

## Chạy

| Chương trình | Lệnh |
|---|---|
| Server | `java -cp out lpp.server.ServerFrame` |
| Host | `java -cp out lpp.host.HostApp` |
| Client | `java -cp out lpp.client.ClientApp` |
| Bot demo | `java -cp out lpp.tools.BotClient join <ip> <mã phòng> 30` |
| Kiểm thử tải | `java -cp out lpp.tools.BotClient test <ip> 50` |

Lần đầu chạy Server, Windows Firewall hỏi quyền cho Java: chọn **Private networks**.
Console Windows hiện tiếng Việt thành `?`: thêm `-Dstdout.encoding=UTF-8` sau `java` (giao diện Swing không bị).

## Test tự động

```bash
java -cp out lpp.common.ProtocolTest && java -cp out lpp.server.RoomTest && java -cp out lpp.server.ServerTest && java -cp out lpp.server.DiscoveryTest
```

## Gửi cho người khác test

```bash
sh dist.sh
```
Tạo `dist/LivePolling.zip` gồm `Server.jar`, `Host.jar`, `Client.jar` (double-click để chạy, máy cần Java 17+) và `HUONG_DAN_TEST.txt` (các bước + danh sách cần thử).

## Gửi lệnh bằng tay (telnet)

```bash
telnet <ip> 5000
```
Gõ `JOIN|<mã phòng>|Telnet` rồi `OPEN_POLL|9|x|a|b` → nhận `ERROR|NOT_ALLOWED|...`.
