package com.cheotdanchu.dto;

import com.cheotdanchu.entity.AllowanceChange;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/** 용돈 변경 저장의 요청과 응답 */
public final class AllowanceChangeDtos {

    private AllowanceChangeDtos() {}

    /** reason 과 suggestionId 는 선택. AI 제안에서 온 저장이면 suggestionId 를 함께 보낸다 */
    public record SaveRequest(
            @Min(1) int amount,
            AllowanceChange.Reason reason,
            @Min(1) @Max(28) int allowanceDay,
            @NotNull Boolean autoTransfer,
            @Min(1) @Max(28) int settlementDay,
            Long suggestionId
    ) {}

    public record SaveResponse(
            Long changeId,
            Integer prevAmount,
            int amount,
            LocalDate effectiveFrom,
            boolean notifiedChild
    ) {}

    /**
     * 저장하면 아이에게 가는 알림 문구. 금액이 이미 정해진 값과 같으면 저장해도 알림이 가지 않아서 null.
     * diff 는 지금 정해진 용돈과의 차이이고, 용돈을 정한 적이 없으면 null
     */
    public record PreviewResponse(
            String message,
            Integer diff,
            LocalDate effectiveFrom
    ) {}
}
