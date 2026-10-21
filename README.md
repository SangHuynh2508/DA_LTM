# LPP – Real-time Live Polling

Đồ án Lập trình mạng: Server / Host / Client giao tiếp bằng giao thức text LPP qua TCP 5000, tìm server bằng UDP 5001.
Đặc tả: `docs/superpowers/specs/2026-10-21-live-polling-design.md`.

## Build (JDK 17+)

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

## Gửi lệnh bằng tay (telnet)

```bash
telnet <ip> 5000
```
Gõ `JOIN|<mã phòng>|Telnet` rồi `OPEN_POLL|9|x|a|b` → nhận `ERROR|NOT_ALLOWED|...`.
