package com.cheotdanchu.ai;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 키워드 검색. Colab 노트북의 search()와 똑같이 계산해요. (규칙을 바꾸면 양쪽 다 바꿔 주세요)
 *
 * 점수 = 3 × (제목과 겹친 비율)
 *      + 3 × (aliases 중 제일 많이 겹친 비율)
 *      + 1 × (본문과 겹친 비율)
 *      + 2   (aliases나 제목이 질문 안에 통째로 들어 있으면)
 * 겹친 비율 = 질문의 두 글자 조각 중 카드에도 있는 조각의 비율
 */
@Component
public class RagSearch {

    public record Hit(double score, RagCard card) {}

    private static final Set<String> STOP = bigrams("뭐야 뭐임 어떻게 어케 알려줘 설명해줘 이거 그거 해도돼 되나 있어 하는거 거야 인데 하면 해줘 야");

    private final RagRepository repo;
    private final AiProperties props;

    public RagSearch(RagRepository repo, AiProperties props) {
        this.repo = repo;
        this.props = props;
    }

    public List<Hit> search(String query) {
        return search(query, props.topK(), props.minScore());
    }

    public List<Hit> search(String query, int topK, double minScore) {
        List<Hit> ranked = new ArrayList<>();
        for (RagCard c : repo.all()) ranked.add(new Hit(score(query, c), c));
        ranked.sort(Comparator.comparingDouble(Hit::score).reversed());   // 같은 점수는 읽은 순서 유지

        List<Hit> out = new ArrayList<>();
        for (Hit h : ranked.subList(0, Math.min(topK, ranked.size()))) {
            if (h.score() >= minScore) out.add(h);
        }
        return out;
    }

    static double score(String query, RagCard card) {
        Set<String> q = bigrams(query);
        q.removeAll(STOP);
        if (q.isEmpty()) return 0.0;

        double s = 3.0 * cover(q, card.title());
        double best = 0;
        for (String a : card.aliases()) best = Math.max(best, cover(q, a));
        s += 3.0 * best;
        s += 1.0 * cover(q, card.body());

        String qn = norm(query);
        List<String> names = new ArrayList<>(card.aliases());
        names.add(card.title());
        for (String a : names) {
            String an = norm(a);
            if (an.length() >= 2 && qn.contains(an)) {
                s += 2.0;
                break;
            }
        }
        return new BigDecimal(s).setScale(3, RoundingMode.HALF_EVEN).doubleValue();
    }

    private static double cover(Set<String> q, String text) {
        Set<String> t = bigrams(text);
        int n = 0;
        for (String g : q) if (t.contains(g)) n++;
        return (double) n / q.size();
    }

    static String norm(String s) {
        if (s == null) return "";
        return s.toLowerCase(Locale.ROOT).replaceAll("[^0-9a-zA-Z가-힣]", "");
    }

    static Set<String> bigrams(String s) {
        String n = norm(s);
        Set<String> out = new LinkedHashSet<>();
        if (n.length() > 1) {
            for (int i = 0; i < n.length() - 1; i++) out.add(n.substring(i, i + 2));
        } else if (!n.isEmpty()) {
            out.add(n);
        }
        return out;
    }
}
