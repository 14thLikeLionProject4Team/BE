package com.cheotdanchu.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 대화창 흐름. Colab 노트북의 chat()과 같은 순서예요.
 *   위기 키워드 → 고정 위기 안내 (분류 전에 먼저 검사, LLM 안 씀)
 *   분류 → 금융지식: 검색 → RAG 답변 → 검사
 *        → 고민: 고민 대화 (위험 신호면 고정 안내)
 *        → 내숫자: MyNumbersProvider (LLM 안 씀)
 *        → 범위밖: 고정 문장
 */
@Service
public class ChatFlowService {

    private static final Logger log = LoggerFactory.getLogger(ChatFlowService.class);

    private static final Set<String> KINDS = Set.of("금융지식", "고민", "내숫자", "범위밖");
    private static final Set<String> MEMO_TOPICS = Set.of("용돈 금액", "지출", "미션", "결제일·부족", "부모님과의 대화", "기타");
    private static final Set<String> MEMO_STATES = Set.of("전하고 싶어함", "아직 고민 중", "해결됨", "전하지 않기를 원함");
    private static final Set<String> DRAFTABLE = Set.of("전하고 싶어함", "아직 고민 중");

    private final GroqClient llm;
    private final PromptLoader prompts;
    private final RagSearch search;
    private final RagRepository repo;
    private final ChatSessionStore store;
    private final AiProperties props;
    private final MyNumbersProvider myNumbers;

    public ChatFlowService(GroqClient llm, PromptLoader prompts, RagSearch search, RagRepository repo,
                           ChatSessionStore store, AiProperties props,
                           Optional<MyNumbersProvider> myNumbers) {
        this.llm = llm;
        this.prompts = prompts;
        this.search = search;
        this.repo = repo;
        this.store = store;
        this.props = props;
        this.myNumbers = myNumbers.orElse((personaId, q) -> AnswerGuard.MY_NUMBERS_DEFAULT);
    }

    // ───────────────────────── 대화 한 번 ─────────────────────────

    public AiDtos.ChatResponse chat(String personaId, String childName, String message) {
        if (message == null || message.isBlank()) {
            return simple("범위밖", "메시지를 입력해 주세요.");
        }
        if (AnswerGuard.isCrisis(message)) {
            // 이 대화는 메모로 만들지 않도록 저장하지 않아요. 부모님께도 알리지 않아요.
            return new AiDtos.ChatResponse("고민", AnswerGuard.CRISIS_REPLY, List.of(), List.of(), List.of(), true, null);
        }
        String kind = classify(message);
        return switch (kind) {
            case "금융지식" -> answerFinance(message);
            case "고민" -> worryChat(personaId, childName, message);
            case "내숫자" -> simple("내숫자", myNumbers.answer(personaId, message));
            default -> simple("범위밖", AnswerGuard.OUT_OF_SCOPE);
        };
    }

    String classify(String message) {
        Map<String, Object> res = llm.chatJson(prompts.build("classify"), message);
        Object kind = res == null ? null : res.get("kind");
        if (kind == null || !KINDS.contains(kind.toString())) {
            log.warn("분류 실패 → 금융지식으로 처리: {}", res);
            return "금융지식";
        }
        return kind.toString();
    }

    AiDtos.ChatResponse answerFinance(String question) {
        List<RagSearch.Hit> hits = search.search(question);
        List<String> retrieved = hits.stream().map(h -> h.card().id()).toList();

        if (hits.isEmpty()) {
            return new AiDtos.ChatResponse("금융지식", AnswerGuard.REFUSAL, List.of(), List.of(), retrieved,
                    false, "검색 결과 없음(LLM 안 부름)");
        }

        String system = prompts.build("rag_answer", Map.of("cards", cardsToText(hits)));
        Map<String, Object> res = llm.chatJson(system, question);
        RagCard top = hits.get(0).card();

        if (res == null || !(res.get("answer") instanceof String answer) || !(res.get("used_ids") instanceof List<?> rawIds)) {
            return cardBody(top, retrieved, "JSON 형식 오류 → 1등 카드 본문");
        }
        List<String> usedIds = rawIds.stream().map(String::valueOf).toList();

        for (String id : usedIds) {
            if (!retrieved.contains(id)) return cardBody(top, retrieved, "넣어주지 않은 id 사용: " + id);
        }
        String bad = AnswerGuard.forbiddenIn(answer);
        if (bad != null) return cardBody(top, retrieved, "금지어: " + bad);

        for (String id : usedIds) {
            RagCard c = repo.byId(id);
            if ("safety".equals(c.category())) {
                Set<String> need = AnswerGuard.contactsIn(c.body());
                Set<String> got = AnswerGuard.contactsIn(answer);
                if (!need.isEmpty() && need.stream().noneMatch(got::contains)) {
                    return cardBody(c, retrieved, "안전 카드 연락처 빠짐");
                }
            }
        }

        List<RagCard.Source> sources = new ArrayList<>();
        for (String id : usedIds) {
            for (RagCard.Source s : repo.byId(id).sources()) if (!sources.contains(s)) sources.add(s);
        }
        return new AiDtos.ChatResponse("금융지식", answer, sources, usedIds, retrieved, false, null);
    }

    AiDtos.ChatResponse worryChat(String personaId, String childName, String message) {
        String name = (childName == null || childName.isBlank()) ? "친구" : childName;
        String system = prompts.build("worry_chat", Map.of("child_name", name));
        Map<String, Object> res = llm.chatJson(system, store.recentWorry(personaId, props.historyTurns()), message);

        if (res != null && Boolean.TRUE.equals(res.get("danger"))) {
            // 위기 신호: 고정 안내. 이 대화는 메모로 만들지 않도록 저장하지 않아요.
            return new AiDtos.ChatResponse("고민", AnswerGuard.CRISIS_REPLY, List.of(), List.of(), List.of(), true, null);
        }

        String reply = res == null ? null : asText(res.get("reply"));
        String fallback = null;
        if (reply == null || reply.isBlank()) {
            reply = AnswerGuard.WORRY_FALLBACK;
            fallback = "JSON 형식 오류 → 고정 문장";
        } else if (AnswerGuard.forbiddenIn(reply) != null) {
            fallback = "금지어: " + AnswerGuard.forbiddenIn(reply);
            reply = AnswerGuard.WORRY_FALLBACK;
        }
        store.addWorryTurn(personaId, message, reply);
        return new AiDtos.ChatResponse("고민", reply, List.of(), List.of(), List.of(), false, fallback);
    }

    // ───────────────────────── 대화창을 나갈 때 ─────────────────────────

    /** 고민 대화를 메모로 정리하고 대화는 지워요. 메모는 아이에게만 보여요. */
    public List<Map<String, Object>> endChat(String personaId) {
        String conversation = store.worryConversationText(personaId);
        if (conversation.isEmpty()) return store.memos(personaId);

        Map<String, Object> res = llm.chatJson(
                prompts.build("memo_extract", Map.of("conversation", conversation)), "메모를 정리해 줘");

        List<Map<String, Object>> memos = new ArrayList<>();
        if (res != null && res.get("memos") instanceof List<?> list) {
            for (Object o : list) {
                if (!(o instanceof Map<?, ?> m)) continue;
                String topic = asText(m.get("주제"));
                String state = asText(m.get("상태"));
                if (topic == null || state == null || !MEMO_TOPICS.contains(topic) || !MEMO_STATES.contains(state)) continue;
                Map<String, Object> memo = new LinkedHashMap<>();
                memo.put("주제", topic);
                memo.put("요청", asText(m.get("요청")));
                memo.put("이유", asText(m.get("이유")));
                memo.put("상태", state);
                memos.add(memo);
                if (memos.size() == 3) break;
            }
        } else {
            log.warn("메모 추출 실패 → 메모 없음");
        }

        List<Map<String, Object>> all = new ArrayList<>(store.memos(personaId));
        all.addAll(memos);
        store.setMemos(personaId, all);
        store.clearWorry(personaId);
        return all;
    }

    /** 부모님께 보낼 "전할 말" 초안. 아이가 고치고 승인한 것만 부모님께 가요. */
    public List<AiDtos.Draft> drafts(String personaId, String childName) {
        List<Map<String, Object>> memos = store.memos(personaId).stream()
                .filter(m -> DRAFTABLE.contains(String.valueOf(m.get("상태"))))
                .toList();
        if (memos.isEmpty()) return List.of();

        String name = (childName == null || childName.isBlank()) ? "친구" : childName;
        Map<String, Object> res = llm.chatJson(
                prompts.build("message_draft", Map.of("child_name", name, "memos", JsonUtil.write(memos))),
                "초안을 써 줘");

        List<AiDtos.Draft> out = new ArrayList<>();
        if (res != null && res.get("drafts") instanceof List<?> list) {
            for (Object o : list) {
                if (!(o instanceof Map<?, ?> d)) continue;
                String text = asText(d.get("text"));
                Integer idx = asInt(d.get("memo_index"));
                if (text == null || idx == null || idx < 0 || idx >= memos.size()) continue;
                if (AnswerGuard.forbiddenIn(text) != null) continue;
                out.add(new AiDtos.Draft(text, memos.get(idx)));
            }
        }
        if (out.isEmpty()) {
            // 실패하면 메모의 '요청'을 그대로 초안으로
            for (Map<String, Object> m : memos) {
                String req = asText(m.get("요청"));
                if (req != null) out.add(new AiDtos.Draft(req, m));
            }
        }
        return out;
    }

    // ───────────────────────── 도우미 ─────────────────────────

    static String cardsToText(List<RagSearch.Hit> hits) {
        StringBuilder sb = new StringBuilder();
        int n = 1;
        for (RagSearch.Hit h : hits) {
            RagCard c = h.card();
            sb.append('(').append(n++).append(") id: ").append(c.id()).append(" | 종류: ").append(c.category()).append('\n');
            sb.append("제목: ").append(c.title()).append('\n');
            sb.append("내용: ").append(c.body()).append('\n');
            if (c.example() != null) sb.append("예시: ").append(c.example()).append('\n');
            if (c.relatedScreen() != null) sb.append("화면: ").append(c.relatedScreen()).append('\n');
            if (!c.sources().isEmpty()) {
                sb.append("출처: ").append(String.join(", ", c.sources().stream().map(RagCard.Source::name).toList())).append('\n');
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    private AiDtos.ChatResponse cardBody(RagCard card, List<String> retrieved, String reason) {
        log.info("RAG 답변 대체: {}", reason);
        return new AiDtos.ChatResponse("금융지식", card.body(), card.sources(), List.of(card.id()), retrieved, false, reason);
    }

    private static AiDtos.ChatResponse simple(String kind, String answer) {
        return new AiDtos.ChatResponse(kind, answer, List.of(), List.of(), List.of(), false, null);
    }

    private static String asText(Object o) {
        return (o == null || o.toString().isBlank() || "null".equals(o.toString())) ? null : o.toString();
    }

    private static Integer asInt(Object o) {
        if (o instanceof Number n) return n.intValue();
        try {
            return o == null ? null : Integer.parseInt(o.toString().trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
