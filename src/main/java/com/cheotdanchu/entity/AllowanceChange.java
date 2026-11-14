package com.cheotdanchu.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "allowance_changes")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AllowanceChange {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long changeId;

    private Long familyId;

    /** 바뀌기 전 월 용돈. 처음 정한 용돈이면 null */
    private Integer prevAmount;

    /** 변경 후 월 용돈 */
    private int amount;

    /** 이 금액이 적용되기 시작하는 날 */
    private LocalDate effectiveFrom;

    @Enumerated(EnumType.STRING)
    private Reason reason;

    @Enumerated(EnumType.STRING)
    private Source source;

    private LocalDateTime createdAt;

    public static AllowanceChange of(Long familyId, Integer prevAmount, int amount, LocalDate effectiveFrom,
                                     Reason reason, Source source, LocalDateTime createdAt) {
        AllowanceChange c = new AllowanceChange();
        c.familyId = familyId;
        c.prevAmount = prevAmount;
        c.amount = amount;
        c.effectiveFrom = effectiveFrom;
        c.reason = reason;
        c.source = source;
        c.createdAt = createdAt;
        return c;
    }

    /** 바꾸는 이유: 처음 / 물가 / 진학 / 다시 약속 / 기타 */
    public enum Reason { INITIAL, INFLATION, SCHOOL, RENEGOTIATION, ETC }

    /** 누가 정했나: 부모가 직접 / AI 제안을 적용 */
    public enum Source { PARENT, AI_SUGGESTION }
}
