package com.cheotdanchu.ai;

import java.util.List;
import java.util.Map;

/** AI API 요청/응답 모양. */
public final class AiDtos {

    private AiDtos() {}

    /** POST /api/ai/chat */
    public record ChatRequest(String personaId, String childName, String message) {}

    /**
     * kind: 금융지식 | 고민 | 내숫자 | 범위밖
     * sources: 답 아래에 보여줄 출처 (금융지식일 때만)
     * crisis: true면 위기 안내 문장이에요. 화면에서 강조해 주세요.
     * fallback: 검사에 걸려 대체 문장을 썼으면 그 이유 (정상이면 null). 화면에는 안 보여줘도 돼요.
     */
    public record ChatResponse(
            String kind,
            String answer,
            List<RagCard.Source> sources,
            List<String> usedIds,
            List<String> retrievedIds,
            boolean crisis,
            String fallback) {}

    /** POST /api/ai/chat/end : 대화창을 나갈 때 */
    public record EndChatRequest(String personaId) {}

    /** 메모 하나 = {"주제","요청","이유","상태"} */
    public record MemosResponse(List<Map<String, Object>> memos) {}

    /** POST /api/ai/chat/drafts : 전할 말 승인 화면을 열 때 */
    public record DraftsRequest(String personaId, String childName) {}

    /** memo는 이 초안이 어느 메모에서 나왔는지 */
    public record Draft(String text, Map<String, Object> memo) {}

    public record DraftsResponse(List<Draft> drafts) {}
}
