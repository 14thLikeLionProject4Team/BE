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

import java.time.LocalDateTime;

/** 부모 홈의 월간 브리핑. 매달 1일에 지난 정산 달을 다섯 줄로 정리한다 (총액만, 거래 목록 없음) */
@Entity
@Table(name = "briefings")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Briefing {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long briefingId;

    @Column(nullable = false)
    private Long familyId;

    /** 대상 정산 달 (예: 2026-09) */
    @Column(nullable = false, length = 7)
    private String targetMonth;

    private String spentLine;
    private String savedLine;
    private String changedLine;
    private String goodLine;
    private String togetherLine;

    private LocalDateTime readAt;

    @Column(insertable = false, updatable = false)
    private LocalDateTime createdAt;

    /** 이미 읽은 브리핑이면 처음 읽은 시각을 그대로 둔다 */
    public void markRead(LocalDateTime at) {
        if (readAt == null) readAt = at;
    }
}
