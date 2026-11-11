package com.cheotdanchu.dto;

import java.time.LocalDate;

/** 부모 홈 "이번 정산 기간" 카드. 부모에게는 총액만 보여주므로 가맹점·결제 목록·부족 예상은 없다 */
public record ChildSpendingResponse(
        LocalDate dueDate,   // 진행 중인 정산이 없으면 null
        Long dDay,           // 진행 중인 정산이 없으면 null
        long totalSpent,
        Allowance allowance, // 용돈 설정이 없으면 null
        String notice
) {
    public record Allowance(String month, int amount, boolean autoTransfer, boolean sent) {}
}
