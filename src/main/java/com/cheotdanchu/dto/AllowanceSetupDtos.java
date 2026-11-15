package com.cheotdanchu.dto;

import com.cheotdanchu.entity.AllowanceChange;

import java.util.List;

/** 용돈 정하기 화면 조회 응답 */
public final class AllowanceSetupDtos {

    private AllowanceSetupDtos() {}

    /**
     * currentAmount 는 용돈을 아직 정한 적이 없으면 null.
     * pendingSuggestion 은 대기 중인 AI 제안이 없으면 null, referenceRange 는 AI 제안이 한 번도 없었으면 null.
     */
    public record SetupResponse(
            Integer currentAmount,
            int allowanceDay,
            boolean autoTransfer,
            int settlementDay,
            ReferenceRange referenceRange,
            PendingSuggestion pendingSuggestion,
            List<ReasonOption> reasons
    ) {}

    public record ReferenceRange(int min, int max) {}

    public record PendingSuggestion(Long suggestionId, int suggestedAmount, String explanation) {}

    /** 바꾸는 이유 선택지 */
    public record ReasonOption(AllowanceChange.Reason code, String label) {}
}
