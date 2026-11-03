package com.cheotdanchu.ai;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 대화창 API. 월말 회고·브리핑·결정 카드는 API를 따로 만들지 않고,
 * 백엔드의 홈 API 안에서 MonthlyTextService를 불러 쓰면 돼요.
 */
@RestController
@RequestMapping("/api/ai")
public class AiController {

    private final ChatFlowService chat;

    public AiController(ChatFlowService chat) {
        this.chat = chat;
    }

    /** 대화창에서 메시지를 보낼 때 */
    @PostMapping("/chat")
    public AiDtos.ChatResponse chat(@RequestBody AiDtos.ChatRequest req) {
        return chat.chat(req.personaId(), req.childName(), req.message());
    }

    /** 대화창을 나갈 때: 고민 대화를 메모로 정리 */
    @PostMapping("/chat/end")
    public AiDtos.MemosResponse end(@RequestBody AiDtos.EndChatRequest req) {
        return new AiDtos.MemosResponse(chat.endChat(req.personaId()));
    }

    /** 전할 말 승인 화면을 열 때: 초안 만들기 */
    @PostMapping("/chat/drafts")
    public AiDtos.DraftsResponse drafts(@RequestBody AiDtos.DraftsRequest req) {
        return new AiDtos.DraftsResponse(chat.drafts(req.personaId(), req.childName()));
    }
}
