package com.cheotdanchu.controller;

import com.cheotdanchu.common.response.ApiResponse;
import com.cheotdanchu.common.response.SuccessStatus;
import com.cheotdanchu.dto.ConversationDtos;
import com.cheotdanchu.service.ConversationService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class ConversationController {

    private final ConversationService conversationService;

    @Operation(summary = "대화 시작 정보", description = "자녀 전용. 대화 탭 첫 인사(이번 정산 결제일까지 남은 날), 부모 비공개 안내 문구, 추천 칩.")
    @GetMapping("/conversations/starter")
    public ApiResponse<ConversationDtos.StarterResponse> getStarter(@RequestHeader("X-Persona-Id") Long personaId) {
        return ApiResponse.onSuccess(SuccessStatus.CONVERSATION_STARTER_OK, conversationService.getStarter(personaId));
    }

    @Operation(summary = "대화 기록 조회", description = "자녀 전용(부모 403). 내 대화 최신순, 20개씩. cursor는 이전 페이지의 nextCursor.")
    @GetMapping("/conversations/messages")
    public ApiResponse<ConversationDtos.MessagesResponse> getMessages(
            @RequestHeader("X-Persona-Id") Long personaId,
            @RequestParam(required = false) Long cursor) {
        return ApiResponse.onSuccess(SuccessStatus.CONVERSATION_MESSAGES_OK, conversationService.getMessages(personaId, cursor));
    }

    @Operation(summary = "메시지 전송", description = "자녀 전용. 메시지를 보내고 첫단추 답을 받는다. cardType: TEXT(일반·고민), RAG(금융 설명, payload.inScope·sources), NUMBER(내 숫자). 고민 대화에서 부모님께 바라는 게 나오면 childMessage에 전할 말 후보(CANDIDATE). 빈 메시지 400, LLM 호출 실패 503.")
    @PostMapping("/conversations/messages")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ConversationDtos.SendResponse> send(
            @RequestHeader("X-Persona-Id") Long personaId,
            @RequestBody ConversationDtos.SendRequest request) {
        return ApiResponse.onSuccess(SuccessStatus.MESSAGE_SENT, conversationService.send(personaId, request.content()));
    }
}
