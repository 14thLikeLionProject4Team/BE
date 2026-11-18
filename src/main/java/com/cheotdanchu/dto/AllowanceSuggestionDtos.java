package com.cheotdanchu.dto;

import com.cheotdanchu.entity.AllowanceSuggestion;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** AI 용돈 제안 조회·처리의 요청과 응답. 부모 홈 카드와 용돈 정하기 화면이 같이 쓴다 */
public final class AllowanceSuggestionDtos {

    private AllowanceSuggestionDtos() {}

    /** 카테고리별 월 사용액과 물가 변동률 */
    public record Basis(String category, int monthlySpent, double changeRate) {}

    public record SuggestionResponse(
            Long suggestionId,
            AllowanceSuggestion.Status status,
            int currentAmount,
            int suggestedAmount,
            int rangeMin,
            int rangeMax,
            BigDecimal inflationRate,
            List<Basis> basis,
            String explanation,
            LocalDateTime createdAt
    ) {}

    /** status: APPLIED(다음 달부터 적용) 또는 KEPT(유지) */
    public record DecisionRequest(@NotNull AllowanceSuggestion.Status status) {}

    /** 적용하면 amount 는 제안 금액이고 effectiveFrom 이 적용 시작일, 유지하면 amount 는 현재 용돈이고 effectiveFrom 은 null */
    public record DecisionResponse(
            Long suggestionId,
            AllowanceSuggestion.Status status,
            int amount,
            LocalDate effectiveFrom
    ) {}
}
