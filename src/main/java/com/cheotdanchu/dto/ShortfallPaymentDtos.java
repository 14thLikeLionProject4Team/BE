package com.cheotdanchu.dto;

import com.cheotdanchu.entity.Settlement;
import com.cheotdanchu.entity.Transfer;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** 부족분 갚기의 요청과 응답 */
public final class ShortfallPaymentDtos {

    private ShortfallPaymentDtos() {}

    /** source: CARRYOVER(지난달까지 남은 돈) 또는 DIRECT(직접 송금). amount: 갚을 금액(1원 이상) */
    public record Request(@NotNull Transfer.Source source, @Min(1) int amount) {}

    /**
     * carryoverAmount 는 갚은 뒤 지난달까지 남은 돈이고, remainingShortfall 은 아직 갚을 부족분이다.
     * 전부 갚으면 status 가 COMPLETED·result 가 SELF_RESOLVED 가 되고, 일부만 갚으면 PARTIAL 이다.
     */
    public record Response(
            Long transferId,
            Long settlementId,
            Transfer.Source source,
            int amount,
            int carryoverAmount,
            long remainingShortfall,
            Settlement.Status status,
            Settlement.Result result
    ) {}
}
