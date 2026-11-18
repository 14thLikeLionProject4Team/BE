package com.cheotdanchu.dto;

import com.cheotdanchu.entity.Mission;
import com.cheotdanchu.entity.MissionSubmission;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record MissionDetailResponse(
        Long missionId,
        String title,
        String criteria,
        Mission.MissionType missionType,
        Mission.Origin origin,
        Mission.Status status,
        Integer rewardAmount,
        Integer desiredReward,
        LocalDate startDate,
        LocalDate endDate,
        Mission.RepeatType repeatType,
        Mission.VerifyMethod verifyMethod,
        MissionListResponse.Progress progress,
        List<SubmissionDto> submissions
) {
    public record SubmissionDto(
            Long submissionId,
            MissionSubmission.ReviewStatus reviewStatus,
            List<String> photoUrls,
            LocalDateTime submittedAt
    ) {}
}
