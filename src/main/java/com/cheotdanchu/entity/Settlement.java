package com.cheotdanchu.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "settlements")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Settlement {

    @Id
    private Long settlementId;

    private Long familyId;

    private LocalDate periodStart;
    private LocalDate periodEnd;
    private LocalDateTime dueAt;

    @Enumerated(EnumType.STRING)
    private Status status;

    /** 정산이 끝나면 정해지는 값. 진행 중인 정산은 null */
    private Integer billedAmount;
    private Integer balanceAtDue;

    /** 결제일에 이체된 금액 (부족했다면 청구액보다 작다) */
    private int paidAmount;

    /** 정산이 끝난 시각. 완납은 결제일 정산 시각, 부족분을 갚았거나 자동 정산됐으면 그 시각. 끝나지 않았으면 null */
    private LocalDateTime resolvedAt;

    @Enumerated(EnumType.STRING)
    private Result result;

    /** 기록 지표·확인서에서 빼는 정산 (부모 지원, 현금 정산) */
    @Column(name = "is_excluded")
    private boolean excluded;

    /**
     * 아직 갚을 부족분 = 청구액 − 결제일에 이체된 금액 − 이미 갚은 것. 결제일이 지나 부족이 남은 정산(SHORTFALL·PARTIAL)만 있고,
     * 정산 전(SCHEDULED·REQUIRED)이거나 끝난(COMPLETED) 정산은 0이다. 부족분 갚기와 정산 상세가 같은 계산을 쓴다.
     */
    public long remainingShortfall(long billed, long paidAtDue, long repaid) {
        if (status != Status.SHORTFALL && status != Status.PARTIAL) return 0;
        return Math.max(billed - paidAtDue - repaid, 0);
    }

    /** 부족분을 일부만 갚았다: 일부 정산을 유지한다 */
    public void markPartial() {
        status = Status.PARTIAL;
    }

    /** 부족분을 전부 갚아 스스로 해소했다 */
    public void resolveBySelf(LocalDateTime at) {
        status = Status.COMPLETED;
        result = Result.SELF_RESOLVED;
        resolvedAt = at;
    }

    public enum Status { SCHEDULED, REQUIRED, SHORTFALL, PARTIAL, COMPLETED }

    /** 월별 결과: 완납 / 부족분 스스로 해소 / 다음 용돈에서 자동 정산 */
    public enum Result { FULL_PAID, SELF_RESOLVED, AUTO_DEDUCTED }
}
