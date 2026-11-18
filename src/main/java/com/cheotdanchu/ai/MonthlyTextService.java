package com.cheotdanchu.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * 월말 회고(아이), 월간 브리핑(부모), 결정 카드(부모) 문장을 만들어요.
 *
 * facts는 백엔드가 원장에서 계산해서 넘겨요. 금액은 "61,500원"처럼 글자로 넣어 주세요.
 * 항목 이름은 각 프롬프트 md의 [사실 항목 설명]과 같아야 해요.
 * 브리핑 facts에는 부모님이 볼 수 있는 값만 넣어 주세요. (사용처, 항목별 금액, D-6, 대화 내용 X)
 *
 * cacheKey를 주면 한 번 만든 글을 기억해요. 예: "A:2026-09". null이면 매번 새로 만들어요.
 * LLM이 실패하거나 검사에 걸리면 틀 문장을 돌려주고, 결과에 "_fallback": true 가 붙어요.
 */
@Service
public class MonthlyTextService {

    private static final Logger log = LoggerFactory.getLogger(MonthlyTextService.class);

    private final GroqClient llm;
    private final PromptLoader prompts;
    private final Map<String, Map<String, Object>> cache = new ConcurrentHashMap<>();

    public MonthlyTextService(GroqClient llm, PromptLoader prompts) {
        this.llm = llm;
        this.prompts = prompts;
    }

    /** 아이용 월말 회고 → {headline, body, next_tip} */
    public Map<String, Object> review(String cacheKey, Map<String, Object> facts) {
        return generate("monthly_review", "회고를 써 줘", List.of("headline", "body", "next_tip"),
                cacheKey, facts, MonthlyTextService::reviewFallback);
    }

    /** 부모용 월간 브리핑 → {headline, body, suggestion}. "아이가 전하는 말"은 승인된 문장을 백엔드가 따로 붙여요. */
    public Map<String, Object> briefing(String cacheKey, Map<String, Object> facts) {
        return generate("monthly_briefing", "브리핑을 써 줘", List.of("headline", "body", "suggestion"),
                cacheKey, facts, MonthlyTextService::briefingFallback);
    }

    /** 부모용 결정 카드 → {title, summary, reasons[], note}. 선택지 버튼은 화면에서 붙여요. */
    public Map<String, Object> decisionCard(String cacheKey, Map<String, Object> facts) {
        return generate("decision_card", "카드를 써 줘", List.of("title", "summary", "reasons", "note"),
                cacheKey, facts, MonthlyTextService::decisionFallback);
    }

    public void clearCache() {
        cache.clear();
    }

    private Map<String, Object> generate(String prompt, String userMsg, List<String> keys, String cacheKey,
                                         Map<String, Object> facts,
                                         Function<Map<String, Object>, Map<String, Object>> fallback) {
        String key = cacheKey == null ? null : prompt + ":" + cacheKey;
        if (key != null && cache.containsKey(key)) return cache.get(key);

        String factsJson = JsonUtil.write(facts);
        Map<String, Object> res = llm.chatJson(prompts.build(prompt, Map.of("facts", factsJson)), userMsg);
        String problem = check(res, keys, factsJson);

        Map<String, Object> out;
        if (problem == null) {
            out = new LinkedHashMap<>();
            for (String k : keys) out.put(k, res.get(k));
        } else {
            log.info("{} 대체 문장 사용: {}", prompt, problem);
            out = new LinkedHashMap<>(fallback.apply(facts));
            out.put("_fallback", true);
        }
        if (key != null) cache.put(key, out);
        return out;
    }

    /** 문제가 없으면 null, 있으면 이유. */
    static String check(Map<String, Object> res, List<String> keys, String factsJson) {
        if (res == null) return "LLM 응답 없음";
        StringBuilder all = new StringBuilder();
        for (String k : keys) {
            Object v = res.get(k);
            if (v == null) return "키 없음: " + k;
            if (v instanceof List<?> list) {
                for (Object o : list) all.append(o).append('\n');
            } else {
                all.append(v).append('\n');
            }
        }
        String bad = AnswerGuard.forbiddenIn(all.toString());
        if (bad != null) return "금지어: " + bad;
        List<String> unknown = AnswerGuard.unknownAmounts(all.toString(), factsJson);
        if (!unknown.isEmpty()) return "facts에 없는 금액: " + unknown;
        return null;
    }

    // ───────────── 틀 문장 (LLM 실패 시) ─────────────

    static Map<String, Object> reviewFallback(Map<String, Object> f) {
        String result = s(f, "result");
        String headline = switch (result) {
            case "완납" -> "결제일 정산 완료";
            case "스스로 해소" -> "부족분 스스로 해소";
            default -> "다음 용돈에서 자동 정산";
        };
        StringBuilder body = new StringBuilder("이번 정산 기간 청구액은 " + s(f, "bill") + "이었어요. ");
        switch (result) {
            case "완납" -> body.append("결제일에 모두 정산했어요. ");
            case "스스로 해소" -> body.append("모자랐던 부족분을 스스로 보내서 마무리했어요. ");
            default -> body.append("부족분 ").append(s(f, "shortfall"))
                    .append("은 다음 용돈에서 빠져요. 다음 용돈 전에 직접 보낼 수도 있어요. ");
        }
        return Map.of("headline", headline, "body", body.toString().trim(),
                "next_tip", "다음 달에도 결제일 6일 전에 청구 예정액을 한 번 확인해 봐요.");
    }

    static Map<String, Object> briefingFallback(Map<String, Object> f) {
        String result = s(f, "result");
        String sentence = switch (result) {
            case "완납" -> "결제일에 모두 정산했어요.";
            case "스스로 해소" -> "모자랐던 부족분을 아이가 스스로 보내서 마무리했어요.";
            default -> "부족분은 다음 용돈에서 자동으로 정산돼요.";
        };
        String body = s(f, "cycle") + " 정산 총액은 " + s(f, "bill_total") + "이었어요. " + sentence;
        String suggestion = s(f, "streak").isEmpty()
                ? "이번 달 결제일 기록을 아이와 가볍게 이야기해 보세요."
                : "연속 완납 " + s(f, "streak") + " 기록을 한마디 알아봐 주세요.";
        return Map.of("headline", (s(f, "child_name") + " " + s(f, "cycle") + " 정산 결과").trim(),
                "body", body, "suggestion", suggestion);
    }

    static Map<String, Object> decisionFallback(Map<String, Object> f) {
        List<String> reasons = new ArrayList<>();
        if (f.get("reasons") instanceof List<?> list) {
            for (Object o : list) {
                if (o instanceof List<?> pair && pair.size() >= 2) reasons.add(pair.get(0) + ": " + pair.get(1));
                if (reasons.size() == 3) break;
            }
        }
        return Map.of(
                "title", "용돈 " + s(f, "proposed_allowance") + " 제안",
                "summary", "참고로 " + s(f, "proposed_allowance") + "을 고려해 볼 수 있어요. 바꾸시면 "
                        + s(f, "apply_from") + " 용돈부터 적용돼요.",
                "reasons", reasons,
                "note", "지금 금액을 그대로 두셔도 괜찮아요.");
    }

    private static String s(Map<String, Object> f, String k) {
        Object v = f.get(k);
        return v == null ? "" : v.toString();
    }
}
