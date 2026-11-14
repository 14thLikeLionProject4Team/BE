package com.cheotdanchu.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** AI 용돈 제안. 부모가 "다음 달부터 적용"하거나 "유지"하면 닫힌다 */
@Entity
@Table(name = "allowance_suggestions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AllowanceSuggestion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long suggestionId;

    private Long familyId;

    private int currentAmount;
    private int suggestedAmount;

    /** 참고 구간 */
    private int rangeMin;
    private int rangeMax;

    @Column(precision = 4, scale = 1)
    private BigDecimal inflationRate;

    /** 근거: [{category, monthlySpent, changeRate}] 를 JSON 문자열로 둔다 */
    @JdbcTypeCode(SqlTypes.JSON)
    private String basis;

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    private String explanation;

    @Enumerated(EnumType.STRING)
    private Status status;

    private LocalDateTime createdAt;

    public boolean isPending() {
        return status == Status.PENDING;
    }

    public void apply() {
        status = Status.APPLIED;
    }

    public void keep() {
        status = Status.KEPT;
    }

    public enum Status { PENDING, APPLIED, KEPT }
}
