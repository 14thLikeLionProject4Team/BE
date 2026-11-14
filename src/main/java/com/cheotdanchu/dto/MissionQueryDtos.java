package com.cheotdanchu.dto;

import com.cheotdanchu.entity.Mission;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 미션 조회 API 응답들 (제안 목록, 종료 목록, 승인 대기 목록, 월 보상 합계) */
public final class MissionQueryDtos {

    private MissionQueryDtos() {}

    /** 자녀 화면은 status, 부모 화면은 criteria를 쓴다. 같은 API라 둘 다 준다 */
    public record ProposedListResponse(int count, List<ProposedMission> missions) {}

    public record ProposedMission(
            Long missionId,
            String title,
            Mission.MissionType missionType,
            Mission.Status status,
            String criteria,
            Integer desiredReward,
            LocalDateTime createdAt
    ) {}

    public record EndedListResponse(List<EndedMission> missions, Long nextCursor) {}

    public record EndedMission(
            Long missionId,
            String title,
            Mission.Status status,
            Integer rewardAmount,
            long paidTotal,
            LocalDate endedAt
    ) {}

    public record PendingListResponse(int count, List<PendingSubmission> submissions) {}

    public record PendingSubmission(
            Long submissionId,
            Long missionId,
            String missionTitle,
            Integer rewardAmount,
            List<String> photoUrls,
            LocalDateTime submittedAt
    ) {}

    /** 용돈 설정이 없는 가족이면 capAmount·remainingAmount가 null */
    public record RewardSummaryResponse(
            String month,
            long paidAmount,
            Long capAmount,
            Integer capRate,
            Long remainingAmount
    ) {}
}
