package com.cheotdanchu.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 결제 내역 화면(자녀 전용) 응답 */
public final class PaymentDtos {

    private PaymentDtos() {}

    /** totalAmount 는 항목 금액의 합. 정산 기간 전체면 청구 예정액과 같다 */
    public record CategoryRatioResponse(long totalAmount, List<CategoryRatio> items) {}

    /** ratio 는 전체 대비 비중(%)으로 소수 첫째 자리까지 */
    public record CategoryRatio(String category, long amount, double ratio) {}

    /** 화면 위의 "청구 예정 79,500원 14건"과 결제일에 쓰는 값. billedAmount 는 취소를 합산한 값이고 paymentCount 는 취소를 뺀 건수 */
    public record SummaryResponse(
            Long settlementId,
            LocalDate periodStart,
            LocalDate periodEnd,
            long billedAmount,
            long paymentCount,
            LocalDate dueDate
    ) {}

    /** nextCursor 는 다음 페이지를 읽을 때 그대로 돌려보내는 값(내용은 알 필요 없음). 마지막 페이지면 null. totalCount 는 페이지와 상관없는 고른 기간의 전체 결제 수 */
    public record PaymentListResponse(List<PaymentItem> items, long totalCount, String nextCursor) {}

    /** 가맹점명까지 보이는 건 아이 본인뿐이다. 취소는 isCanceled 가 true 이고 금액이 마이너스 */
    public record PaymentItem(
            Long paymentId,
            LocalDateTime paidAt,
            String merchantName,
            String category,
            int amount,
            boolean isCanceled,
            boolean isHighAmount
    ) {}
}
