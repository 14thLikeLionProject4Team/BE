package com.cheotdanchu.ai;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 페르소나별 고민 대화와 메모를 메모리에 들고 있어요. (시연용. 서버를 끄면 사라져요)
 * 금융 질문 대화는 저장하지 않고, 고민 대화만 저장해요. 메모 추출에 쓰려고요.
 */
@Component
public class ChatSessionStore {

    private static final class Session {
        final List<GroqClient.Message> worryTurns = new ArrayList<>();
        List<Map<String, Object>> memos = List.of();
    }

    private final Map<String, Session> sessions = new ConcurrentHashMap<>();

    private Session of(String personaId) {
        return sessions.computeIfAbsent(personaId, k -> new Session());
    }

    public void addWorryTurn(String personaId, String userText, String assistantText) {
        Session s = of(personaId);
        synchronized (s) {
            s.worryTurns.add(new GroqClient.Message("user", userText));
            s.worryTurns.add(new GroqClient.Message("assistant", assistantText));
        }
    }

    /** 최근 n번 주고받은 대화 (user+assistant 한 쌍이 1번). */
    public List<GroqClient.Message> recentWorry(String personaId, int turns) {
        Session s = of(personaId);
        synchronized (s) {
            int from = Math.max(0, s.worryTurns.size() - turns * 2);
            return List.copyOf(s.worryTurns.subList(from, s.worryTurns.size()));
        }
    }

    /** memo_extract에 넣을 대화 글. 고민 대화가 없으면 빈 문자열. */
    public String worryConversationText(String personaId) {
        Session s = of(personaId);
        synchronized (s) {
            StringBuilder sb = new StringBuilder();
            for (GroqClient.Message m : s.worryTurns) {
                sb.append("user".equals(m.role()) ? "아이: " : "첫단추: ").append(m.content()).append('\n');
            }
            return sb.toString().trim();
        }
    }

    public void clearWorry(String personaId) {
        Session s = of(personaId);
        synchronized (s) {
            s.worryTurns.clear();
        }
    }

    public void setMemos(String personaId, List<Map<String, Object>> memos) {
        of(personaId).memos = List.copyOf(memos);
    }

    public List<Map<String, Object>> memos(String personaId) {
        return of(personaId).memos;
    }
}
