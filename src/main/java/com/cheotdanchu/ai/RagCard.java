package com.cheotdanchu.ai;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * RAG 카드 한 장. service_rules.json, terms_bok700.json, safety_cards.json 의 한 항목이에요.
 * source는 객체 하나여도, 배열이어도 읽을 수 있어요.
 */
public record RagCard(
        String id,
        String category,       // service | term | safety
        String title,
        List<String> aliases,
        String body,
        String example,
        String relatedScreen,
        List<Source> sources) {

    public record Source(String name, String url) {}

    static RagCard fromMap(Map<String, Object> m) {
        return new RagCard(
                str(m.get("id")),
                str(m.get("category")),
                str(m.get("title")),
                strList(m.get("aliases")),
                str(m.get("body")),
                strOrNull(m.get("example")),
                strOrNull(m.get("related_screen")),
                sources(m.get("source")));
    }

    private static List<Source> sources(Object raw) {
        List<Source> out = new ArrayList<>();
        if (raw instanceof Map<?, ?> one) {
            out.add(new Source(str(one.get("name")), strOrNull(one.get("url"))));
        } else if (raw instanceof List<?> many) {
            for (Object o : many) {
                if (o instanceof Map<?, ?> s) out.add(new Source(str(s.get("name")), strOrNull(s.get("url"))));
            }
        }
        return List.copyOf(out);
    }

    private static List<String> strList(Object raw) {
        if (!(raw instanceof List<?> list)) return List.of();
        return list.stream().filter(o -> o != null).map(Object::toString).toList();
    }

    private static String str(Object o) {
        return o == null ? "" : o.toString();
    }

    private static String strOrNull(Object o) {
        return (o == null || o.toString().isBlank()) ? null : o.toString();
    }
}
