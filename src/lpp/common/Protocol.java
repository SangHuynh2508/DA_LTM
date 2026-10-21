package lpp.common;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Giao thức LPP: mỗi thông điệp một dòng UTF-8, các trường cách nhau bởi '|'. */
public final class Protocol {
    public static final int TCP_PORT = 5000;
    public static final int UDP_PORT = 5001;
    public static final int MAX_LINE = 4096;
    public static final int TIMEOUT_MS = 15_000;
    public static final int PING_MS = 5_000;
    public static final String DISCOVER = "DISCOVER_LPP";
    public static final String SERVER_REPLY = "LPP_SERVER";

    private Protocol() {}

    public static final class LineTooLongException extends IOException {
        public LineTooLongException() { super("Dòng dài hơn " + MAX_LINE + " ký tự"); }
    }

    /** Ghép các trường thành một dòng (không kèm '\n'), escape '\', '|' và xuống dòng. */
    public static String encode(String... fields) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < fields.length; i++) {
            if (i > 0) sb.append('|');
            for (char c : fields[i].toCharArray()) {
                switch (c) {
                    case '\\' -> sb.append("\\\\");
                    case '|' -> sb.append("\\|");
                    case '\n' -> sb.append("\\n");
                    case '\r' -> { }
                    default -> sb.append(c);
                }
            }
        }
        return sb.toString();
    }

    /** Tách một dòng thành các trường, bỏ escape. */
    public static String[] decode(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '\\' && i + 1 < line.length()) {
                char n = line.charAt(++i);
                cur.append(n == 'n' ? '\n' : n);
            } else if (c == '|') {
                fields.add(cur.toString());
                cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        fields.add(cur.toString());
        return fields.toArray(new String[0]);
    }

    /** Đọc một dòng (bỏ '\r' cuối); null khi hết luồng. Không dùng readLine() vì nó không giới hạn độ dài. */
    public static String readLine(BufferedReader in) throws IOException {
        StringBuilder sb = new StringBuilder();
        int c;
        while ((c = in.read()) != -1 && c != '\n') {
            if (sb.length() >= MAX_LINE) throw new LineTooLongException();
            sb.append((char) c);
        }
        if (c == -1 && sb.length() == 0) return null;
        int len = sb.length();
        if (len > 0 && sb.charAt(len - 1) == '\r') sb.setLength(len - 1);
        return sb.toString();
    }
}
