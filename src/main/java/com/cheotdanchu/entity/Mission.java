package com.cheotdanchu.entity;

import com.cheotdanchu.dto.MissionCreateRequest;
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
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long missionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "family_id", nullable = false)
    private Family family;

    @Column(nullable = false, length = 50)
    private String title;

    @Column(length = 200)
    private String criteria;

    @Enumerated(EnumType.STRING)
    private MissionType missionType;

    private Integer rewardAmount;
    private Integer desiredReward;

    private LocalDate startDate;
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RepeatType repeatType;

    @Enumerated(EnumType.STRING)
    private VerifyMethod verifyMethod;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Origin origin;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    /** 부모가 만든 미션은 바로 열린다 (NEW) */
    public static Mission createByParent(Family family, MissionCreateRequest req) {
        Mission m = new Mission();
        m.family = family;
        m.title = req.title();
        m.criteria = req.criteria();
        m.missionType = req.missionType();
        m.rewardAmount = req.rewardAmount();
        m.startDate = req.startDate();
        m.endDate = req.endDate();
        m.repeatType = req.repeatType();
        m.verifyMethod = req.verifyMethod();
        m.origin = Origin.PARENT;
        m.status = Status.NEW;
        return m;
    }

    /**
     * 새 미션·진행 중인 미션이고 마감일이 지나지 않았을 때만 완료를 제출할 수 있다. 제출하면 부모 확인 대기(SUBMITTED).
     * 시작일은 검사하지 않는다: 데모 기준일이 고정이라, 시연 중 실제 날짜로 시작일을 넣은 새 미션이 영영 제출되지 않는 일을 막기 위해.
     */
    public boolean canSubmit(LocalDate today) {
        boolean open = status == Status.NEW || status == Status.IN_PROGRESS;
        return open && (endDate == null || !today.isAfter(endDate));
    }

    public void submit() {
        status = Status.SUBMITTED;
    }

    /** 승인 후: 승인된 횟수가 목표 횟수를 채우면 COMPLETED, 아니면 다음 회차를 위해 IN_PROGRESS */
    public void afterApproval(int approvedCount) {
        status = approvedCount >= targetCount() ? Status.COMPLETED : Status.IN_PROGRESS;
    }

    /** 반려 후: 다시 제출할 수 있게 IN_PROGRESS */
    public void afterRejection() {
        status = Status.IN_PROGRESS;
    }

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
    public enum VerifyMethod { PHOTO, DIRECT }
    public enum Origin { PARENT, CHILD, AI }
    public enum Status { RECOMMENDED, PROPOSED, ON_HOLD, NEW, IN_PROGRESS, SUBMITTED, COMPLETED, CANCELED, EXPIRED }
}
