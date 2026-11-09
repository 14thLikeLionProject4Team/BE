package com.cheotdanchu.dto;

import com.cheotdanchu.entity.Mission;

import java.time.LocalDate;
import java.util.List;

public record MissionListResponse(
        int count,
        int totalReward,
        List<MissionDto> missions
) {
    public record MissionDto(
            Long missionId,
            String title,
            Mission.MissionType missionType,
            Mission.Status status,
            Integer rewardAmount,
            Progress progress,
            String description,
            LocalDate startDate,
            LocalDate endDate
    ) {}

    public record Progress(int done, int target, int rate) {}
}
