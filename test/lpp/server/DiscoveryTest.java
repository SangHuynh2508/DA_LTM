package lpp.server;

import lpp.common.Discovery;

import java.util.List;

public class DiscoveryTest {
    public static void main(String[] args) throws Exception {
        DiscoveryResponder responder = new DiscoveryResponder(0, 5000, "May-Test", msg -> { });
        Thread t = new Thread(responder);
        t.setDaemon(true);
        t.start();

        List<Discovery.Found> found = Discovery.find(responder.port(), 1000);
        check(found.stream().anyMatch(f -> f.name().equals("May-Test") && f.port() == 5000),
                "tìm thấy server qua UDP, nhận được: " + found);
        System.out.println("DiscoveryTest OK " + found);
    }

    static void check(boolean ok, String what) { if (!ok) throw new AssertionError("FAIL: " + what); }
}
