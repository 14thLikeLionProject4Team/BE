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

    @Enumerated(EnumType.STRING)
    private Result result;

    /** 기록 지표·확인서에서 빼는 정산 (부모 지원, 현금 정산) */
    @Column(name = "is_excluded")
    private boolean excluded;

    public enum Status { SCHEDULED, REQUIRED, SHORTFALL, PARTIAL, COMPLETED }

    /** 월별 결과: 완납 / 부족분 스스로 해소 / 다음 용돈에서 자동 정산 */
    public enum Result { FULL_PAID, SELF_RESOLVED, AUTO_DEDUCTED }
}
