package com.cheotdanchu.dto;

import com.cheotdanchu.entity.Mission;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record MissionCreateRequest(
        @NotBlank @Size(max = 50) String title,
        @Size(max = 200) String criteria,
        @NotNull Mission.MissionType missionType,
        @NotNull @Positive Integer rewardAmount,
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate,
        @NotNull Mission.RepeatType repeatType,
        @NotNull Mission.VerifyMethod verifyMethod
) {}
