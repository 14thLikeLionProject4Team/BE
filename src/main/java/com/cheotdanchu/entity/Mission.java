package com.cheotdanchu.entity;

import com.cheotdanchu.dto.MissionCreateRequest;
import com.cheotdanchu.dto.MissionProposalRequest;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
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

    /** DB 기본값(CURRENT_TIMESTAMP)으로 채운다 */
    @Column(insertable = false, updatable = false)
    private LocalDateTime createdAt;

    /** 부모가 만든 미션은 바로 열린다 (NEW) */
    public static Mission createByParent(Family family, MissionCreateRequest req) {
        Mission m = new Mission();
        m.family = family;
        m.fill(req);
        m.origin = Origin.PARENT;
        m.status = Status.NEW;
        return m;
    }

    /** 아이가 제안한 미션은 부모가 금액을 정해서 열기 전까지 PROPOSED. 보상은 아직 없고 바라는 보상만 남긴다 */
    public static Mission createByChild(Family family, MissionProposalRequest req) {
        Mission m = new Mission();
        m.family = family;
        m.title = req.title();
        m.criteria = req.criteria();
        m.missionType = req.missionType();
        m.desiredReward = req.desiredReward();
        m.startDate = req.startDate();
        m.endDate = req.endDate();
        m.repeatType = req.repeatType();
        m.verifyMethod = req.verifyMethod();
        m.origin = Origin.CHILD;
        m.status = Status.PROPOSED;
        return m;
    }

    /** 제안(보류 포함)·추천 미션만 부모가 열 수 있다 */
    public boolean canOpen() {
        return status == Status.PROPOSED || status == Status.ON_HOLD || status == Status.RECOMMENDED;
    }

    /** 부모가 내용을 채워서 연다 ("금액 정해서 열기"). 출처(아이 제안·AI 추천)는 그대로 둔다 */
    public void open(MissionCreateRequest req) {
        fill(req);
        status = Status.NEW;
    }

    public boolean canHold() {
        return status == Status.PROPOSED;
    }

    /** 부모가 "다음에"를 누르면 보류. 아이 화면의 내가 제안한 미션에는 계속 보인다 */
    public void hold() {
        status = Status.ON_HOLD;
    }

    /** 아직 열지 않은 제안·보류·추천 미션 (수정·취소 대신 '열기'로 정한다) */
    public boolean isNotOpenedYet() {
        return status == Status.PROPOSED || status == Status.ON_HOLD || status == Status.RECOMMENDED;
    }

    /** 새 미션·진행 중·확인 대기 미션만 부모가 고칠 수 있다 (끝난 미션, 아직 열지 않은 제안·추천은 안 됨) */
    public boolean canEdit() {
        return status == Status.NEW || status == Status.IN_PROGRESS || status == Status.SUBMITTED;
    }

    /** 보낸 필드만 바꾼다 */
    public void edit(String title, String criteria, Integer rewardAmount, LocalDate endDate) {
        if (title != null) this.title = title;
        if (criteria != null) this.criteria = criteria;
        if (rewardAmount != null) this.rewardAmount = rewardAmount;
        if (endDate != null) this.endDate = endDate;
    }

    /**
     * 새 미션·진행 중인 미션만 취소할 수 있다. 확인 대기(SUBMITTED)는 아이가 낸 제출이 확인 대기 목록에 남아 있어서
     * 먼저 승인·반려해야 한다. 취소한 미션은 지우지 않고 CANCELED로 남겨 종료 미션 목록에 보인다
     */
    public boolean canCancel() {
        return status == Status.NEW || status == Status.IN_PROGRESS;
    }

    public void cancel() {
        status = Status.CANCELED;
    }

    private void fill(MissionCreateRequest req) {
        title = req.title();
        criteria = req.criteria();
        missionType = req.missionType();
        rewardAmount = req.rewardAmount();
        startDate = req.startDate();
        endDate = req.endDate();
        repeatType = req.repeatType();
        verifyMethod = req.verifyMethod();
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
