package lpp.server;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Một câu poll. Không tự đồng bộ: mọi truy cập đi qua khoá của Room. */
final class Poll {
    final int id;
    final String question;
    final List<String> options;
    boolean open = true;
    private final Map<String, Integer> votes = new HashMap<>();

    Poll(int id, String question, List<String> options) {
        this.id = id;
        this.question = question;
        this.options = List.copyOf(options);
    }

    /** Ghi đè phiếu cũ nếu có → đổi phiếu không bao giờ đếm trùng. */
    void vote(String name, int option) { votes.put(name, option); }

    Integer voteOf(String name) { return votes.get(name); }

    int[] counts() {
        int[] c = new int[options.size()];
        for (int v : votes.values()) c[v]++;
        return c;
    }
}
