package com.cheotdanchu.dto;

import com.cheotdanchu.entity.Settlement;
import com.cheotdanchu.entity.Transfer;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 정산 목록·상세(마이(자녀)의 결제일 기록, 결제 내역의 월별 상세) 응답 */
public final class SettlementHistoryDtos {

    private SettlementHistoryDtos() {}

    /** nextCursor 는 다음 페이지를 읽을 때 그대로 돌려보내는 값(내용은 알 필요 없음). 마지막 페이지면 null */
    public record ListResponse(List<Item> items, long totalCount, String nextCursor) {}

    /** result 는 정산이 아직 끝나지 않았으면 null. isExcluded 는 기록 지표에서 빠진 정산 */
    public record Item(
            Long settlementId,
            LocalDate periodStart,
            LocalDate periodEnd,
            Settlement.Status status,
            Settlement.Result result,
            Integer billedAmount,
            boolean isExcluded
    ) {}

    /**
     * paidAmount 는 결제일에 이체된 금액(결제일 정산 이체의 합)이고, shortfallAmount 는 결제일에 부족했던 금액(나중에 갚았어도 그때의 부족액)이다.
     * remainingShortfall 은 아직 갚을 부족분으로 일부를 갚았으면 그만큼 줄어든다 (결제일이 지나 부족이 남은 정산만 값이 있고 나머지는 0).
     * resolvedAt 은 정산이 끝난 시각으로 끝나지 않았으면 null.
     */
    public record Detail(
            Long settlementId,
            LocalDate periodStart,
            LocalDate periodEnd,
            Settlement.Status status,
            Settlement.Result result,
            long billedAmount,
            int paidAmount,
            long shortfallAmount,
            long remainingShortfall,
            boolean isExcluded,
            LocalDateTime resolvedAt,
            List<TransferItem> transfers
    ) {}

    /** source 는 부족분 갚기(SHORTFALL_PAYMENT)에서만 값이 있다 */
    public record TransferItem(Transfer.Type type, Transfer.Source source, int amount, LocalDateTime transferredAt) {}
}
