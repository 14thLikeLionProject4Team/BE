package com.cheotdanchu.controller;

import com.cheotdanchu.common.response.ApiResponse;
import com.cheotdanchu.common.response.SuccessStatus;
import com.cheotdanchu.dto.AllowanceSuggestionDtos;
import com.cheotdanchu.entity.AllowanceSuggestion;
import com.cheotdanchu.service.AllowanceSuggestionService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.Locale;

@RestController
@RequiredArgsConstructor
public class AllowanceController {

    private final AllowanceSuggestionService suggestionService;

    @Operation(summary = "AI 용돈 제안 조회",
            description = "부모 전용. 부모 홈 결정 카드와 용돈 정하기 배너가 같이 쓰는 대기 중인 제안과 근거(참고 구간, 물가 상승률, 카테고리별 사용액·변동률). 대기 중인 제안이 없으면 에러가 아니라 data 가 null 입니다.")
    @GetMapping("/allowance/suggestion")
    public ApiResponse<AllowanceSuggestionDtos.SuggestionResponse> getSuggestion(@RequestHeader("X-Persona-Id") Long personaId) {
        return ApiResponse.onSuccess(SuccessStatus.ALLOWANCE_SUGGESTION_OK, suggestionService.getPending(personaId));
    }

    @Operation(summary = "AI 용돈 제안 처리",
            description = "부모 전용. status: APPLIED(다음 달 1일부터 적용, 용돈 변경 이력 + 아이 알림) 또는 KEPT(유지, 제안만 닫음). 이미 처리한 제안은 409, 다른 가족의 제안은 404.")
    @PatchMapping("/allowance/suggestions/{suggestionId}")
    public ApiResponse<AllowanceSuggestionDtos.DecisionResponse> decide(
            @RequestHeader("X-Persona-Id") Long personaId,
            @PathVariable Long suggestionId,
            @Valid @RequestBody AllowanceSuggestionDtos.DecisionRequest request) {
        AllowanceSuggestionDtos.DecisionResponse result = suggestionService.decide(personaId, suggestionId, request.status());
        if (result.status() == AllowanceSuggestion.Status.KEPT) {
            return ApiResponse.onSuccess(SuccessStatus.ALLOWANCE_SUGGESTION_KEPT, result);
        }
        return ApiResponse.onSuccess(SuccessStatus.ALLOWANCE_SUGGESTION_APPLIED,
                String.format(Locale.KOREA, "%d월부터 용돈이 %,d원으로 바뀌어요.", result.effectiveFrom().getMonthValue(), result.amount()),
                result);
    }
}
