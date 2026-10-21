package lpp.common;

import java.io.BufferedReader;
import java.io.StringReader;
import java.util.Arrays;

public class ProtocolTest {
    public static void main(String[] args) throws Exception {
        assertDecode("VOTE|1|2", "VOTE", "1", "2");
        assertDecode("PING", "PING");
        assertDecode("A||B", "A", "", "B");

        String tricky = "a|b\\c\nd";
        String line = Protocol.encode("OPEN_POLL", "1", tricky, "Bún bò");
        check(!line.contains("\n"), "dòng đã encode không được chứa xuống dòng");
        assertDecode(line, "OPEN_POLL", "1", tricky, "Bún bò");

        BufferedReader in = new BufferedReader(new StringReader("PING\r\nJOIN|1|x\nlast"));
        check(Protocol.readLine(in).equals("PING"), "bỏ \\r cuối dòng");
        check(Protocol.readLine(in).equals("JOIN|1|x"), "dòng thứ hai");
        check(Protocol.readLine(in).equals("last"), "dòng cuối không có \\n");
        check(Protocol.readLine(in) == null, "EOF trả null");

        BufferedReader ok = new BufferedReader(new StringReader("x".repeat(Protocol.MAX_LINE) + "\n"));
        check(Protocol.readLine(ok).length() == Protocol.MAX_LINE, "đúng MAX_LINE vẫn hợp lệ");

        BufferedReader big = new BufferedReader(new StringReader("x".repeat(Protocol.MAX_LINE + 1) + "\n"));
        boolean thrown = false;
        try { Protocol.readLine(big); } catch (Protocol.LineTooLongException e) { thrown = true; }
        check(thrown, "dòng dài hơn MAX_LINE phải ném LineTooLongException");

        System.out.println("ProtocolTest OK");
    }

    private static void assertDecode(String line, String... expected) {
        String[] got = Protocol.decode(line);
        check(Arrays.equals(got, expected), Arrays.toString(got) + " != " + Arrays.toString(expected));
    }

    static void check(boolean ok, String what) { if (!ok) throw new AssertionError("FAIL: " + what); }
}
