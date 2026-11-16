package com.cheotdanchu.controller;

import com.cheotdanchu.common.response.ApiResponse;
import com.cheotdanchu.common.response.SuccessStatus;
import com.cheotdanchu.dto.ConversationDtos;
import com.cheotdanchu.service.ConversationService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class ConversationController {

    private final ConversationService conversationService;

    @Operation(summary = "메시지 전송", description = "자녀 전용. 메시지를 보내고 첫단추 답을 받는다. cardType: TEXT(일반·고민), RAG(금융 설명, payload.inScope·sources), NUMBER(내 숫자). 고민 대화에서 부모님께 바라는 게 나오면 childMessage에 전할 말 후보(CANDIDATE). 빈 메시지 400, LLM 호출 실패 503.")
    @PostMapping("/conversations/messages")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ConversationDtos.SendResponse> send(
            @RequestHeader("X-Persona-Id") Long personaId,
            @RequestBody ConversationDtos.SendRequest request) {
        return ApiResponse.onSuccess(SuccessStatus.MESSAGE_SENT, conversationService.send(personaId, request.content()));
    }
}
