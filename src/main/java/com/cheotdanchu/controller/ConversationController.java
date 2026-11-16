package com.cheotdanchu.controller;

import com.cheotdanchu.common.response.ApiResponse;
import com.cheotdanchu.common.response.SuccessStatus;
import com.cheotdanchu.dto.ConversationDtos;
import com.cheotdanchu.service.ConversationService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
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

    @Operation(summary = "대화 기록 삭제", description = "자녀 전용. 내 대화 기록을 모두 지운다. 이미 만든 전할 말은 남고 출처 메시지 연결만 끊긴다.")
    @DeleteMapping("/conversations")
    public ApiResponse<ConversationDtos.DeleteResponse> deleteAll(@RequestHeader("X-Persona-Id") Long personaId) {
        return ApiResponse.onSuccess(SuccessStatus.CONVERSATION_DELETED, conversationService.deleteAll(personaId));
    }

    @Operation(summary = "전할 말 상태 변경", description = "자녀·부모 공용, 역할마다 보낼 수 있는 값이 다르다. 자녀(CANDIDATE일 때): MONTHLY(월말에 전하기)·SENT(지금 전하기, delivery=NOW)·DISCARDED. 부모(SENT일 때): DISCUSS·ACKNOWLEDGED. 다른 가족의 말 404, 지금 상태나 역할에 맞지 않는 값 409. SENT가 되면 부모 결정 카드에 뜨고, 부모가 답하면 사라진다.")
    @PatchMapping("/child-messages/{childMessageId}")
    public ApiResponse<ConversationDtos.StatusResponse> changeStatus(
            @RequestHeader("X-Persona-Id") Long personaId,
            @PathVariable Long childMessageId,
            @RequestBody ConversationDtos.StatusRequest request) {
        ConversationDtos.StatusResponse result = conversationService.changeStatus(personaId, childMessageId, request.status());
        return ApiResponse.onSuccess(SuccessStatus.CHILD_MESSAGE_UPDATED, ConversationService.statusMessage(result.status()), result);
    }

    @Operation(summary = "월말 회고 조회", description = "자녀 전용. 가장 최근에 끝난 정산을 돌아보는 질문과 월말에 전하기로 한(MONTHLY) 전할 말. 끝난 정산이 없으면 available=false.")
    @GetMapping("/retrospects/current")
    public ApiResponse<ConversationDtos.RetrospectResponse> getRetrospect(@RequestHeader("X-Persona-Id") Long personaId) {
        return ApiResponse.onSuccess(SuccessStatus.RETROSPECT_OK, conversationService.getRetrospect(personaId));
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
