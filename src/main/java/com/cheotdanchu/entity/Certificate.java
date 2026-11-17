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

import com.cheotdanchu.dto.RecordsSummaryResponse;

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

    /** PDF 파일 이름 (폴더는 CertificateStorage 가 정한다) */
    private String filePath;

    private LocalDateTime issuedAt;

    private LocalDateTime expiresAt;

    /** 발급할 때의 지표를 그대로 저장한다 */
    public static Certificate issue(Long memberId, RecordsSummaryResponse r, String filePath, LocalDateTime issuedAt, LocalDateTime expiresAt) {
        Certificate c = new Certificate();
        c.memberId = memberId;
        c.fullPaidMonths = r.fullPaidMonths();
        c.totalMonths = r.totalMonths();
        c.maxStreak = r.maxStreak();
        c.avgMarginRate = BigDecimal.valueOf(r.avgMarginRate());
        c.selfResolvedCount = r.selfResolvedCount();
        c.shortfallCount = r.shortfallCount();
        c.filePath = filePath;
        c.issuedAt = issuedAt;
        c.expiresAt = expiresAt;
        return c;
    }

    /**
     * 유효기한이 지났는지. 시연 기준일(SeedRepository.today)은 날짜 단위라서 만료일 당일까지는 유효하다.
     * 최신 조회와 다운로드·공유 링크의 만료 판단은 모두 이 메서드를 써서 같은 확인서가 곳곳에서 다르게 판정되지 않게 한다.
     */
    public boolean isExpiredOn(LocalDate today) {
        return today.isAfter(expiresAt.toLocalDate());
    }
}
