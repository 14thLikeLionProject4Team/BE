package com.cheotdanchu.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

@Entity
@Table(name = "missions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Mission {

    @Id
    private Long missionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "family_id", nullable = false)
    private Family family;

    @Column(nullable = false, length = 50)
    private String title;

    @Enumerated(EnumType.STRING)
    private MissionType missionType;

    private Integer rewardAmount;

    private LocalDate startDate;
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RepeatType repeatType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    /** 목표 횟수: 반복 주기와 기간으로 계산 (기간이 없으면 1회) */
    public int targetCount() {
        if (startDate == null || endDate == null) return 1;
        long days = ChronoUnit.DAYS.between(startDate, endDate) + 1;
        return switch (repeatType) {
            case ONCE -> 1;
            case DAILY -> (int) days;
            case WEEKLY -> (int) Math.ceil(days / 7.0);
            case MONTHLY -> (int) ChronoUnit.MONTHS.between(startDate, endDate) + 1;
        };
    }

    public enum MissionType { CHORE, FINANCE, HABIT }
    public enum RepeatType { ONCE, DAILY, WEEKLY, MONTHLY }
    public enum Status { RECOMMENDED, PROPOSED, ON_HOLD, NEW, IN_PROGRESS, SUBMITTED, COMPLETED, CANCELED, EXPIRED }
}
