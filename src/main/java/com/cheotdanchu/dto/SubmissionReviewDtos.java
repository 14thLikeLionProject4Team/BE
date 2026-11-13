package com.cheotdanchu.dto;

import com.cheotdanchu.entity.Mission;
import com.cheotdanchu.entity.MissionSubmission;
import jakarta.validation.constraints.NotNull;

/** 미션 승인·반려 요청과 응답. 같은 화면의 버튼 두 개라 한 파일에 둔다 */
public final class SubmissionReviewDtos {

    private SubmissionReviewDtos() {}

    public record RejectRequest(@NotNull MissionSubmission.RejectReason reason) {}

    public record ApproveResponse(
            Long submissionId,
            MissionSubmission.ReviewStatus reviewStatus,
            Mission.Status missionStatus,
            Long transferId,
            int rewardAmount
    ) {}

    public record RejectResponse(
            Long submissionId,
            MissionSubmission.ReviewStatus reviewStatus,
            MissionSubmission.RejectReason rejectReason,
            Mission.Status missionStatus
    ) {}
}
