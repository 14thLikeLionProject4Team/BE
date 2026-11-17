package com.cheotdanchu.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 금융행동 기록 확인서. 발급할 때의 지표 4개를 그대로 저장해 둔다 */
@Entity
@Table(name = "certificates")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Certificate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long certificateId;

    private Long memberId;

    @JdbcTypeCode(SqlTypes.TINYINT)
    private int fullPaidMonths;

    @JdbcTypeCode(SqlTypes.TINYINT)
    private int totalMonths;

    @JdbcTypeCode(SqlTypes.TINYINT)
    private int maxStreak;

    @Column(precision = 4, scale = 1)
    private BigDecimal avgMarginRate;

    @JdbcTypeCode(SqlTypes.TINYINT)
    private int selfResolvedCount;

    @JdbcTypeCode(SqlTypes.TINYINT)
    private int shortfallCount;

    private LocalDateTime issuedAt;

    private LocalDateTime expiresAt;

    /**
     * 유효기한이 지났는지. 시연 기준일(SeedRepository.today)은 날짜 단위라서 만료일 당일까지는 유효하다.
     * 최신 조회와 다운로드·공유 링크의 만료 판단은 모두 이 메서드를 써서 같은 확인서가 곳곳에서 다르게 판정되지 않게 한다.
     */
    public boolean isExpiredOn(LocalDate today) {
        return today.isAfter(expiresAt.toLocalDate());
    }
}
