#!/bin/sh
# Đóng gói Server.jar / Host.jar / Client.jar (double-click để chạy, cần Java 17+) vào dist/ và nén thành LivePolling.zip.
# Chạy trong Git Bash: sh dist.sh
set -e
rm -rf out dist && mkdir -p dist
javac --release 17 -encoding UTF-8 -d out $(find src -name '*.java')
cp -r src/lpp/common/fonts out/lpp/common/   # font Be Vietnam Pro đóng vào jar
for app in Server:lpp.server.ServerFrame Host:lpp.host.HostApp Client:lpp.client.ClientApp; do
    jar --create --file "dist/${app%%:*}.jar" --main-class "${app#*:}" -C out .
done
cp HUONG_DAN_TEST.txt dist/
(cd dist && jar --create --no-manifest --file LivePolling.zip *.jar HUONG_DAN_TEST.txt)
ls -l dist
