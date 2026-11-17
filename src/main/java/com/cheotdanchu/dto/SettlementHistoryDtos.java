package com.cheotdanchu.dto;

import com.cheotdanchu.entity.Settlement;

import java.time.LocalDate;
import java.util.List;

/** 정산 목록(마이(자녀)의 결제일 기록) 응답 */
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
}
