package com.cheotdanchu.dto;

import com.cheotdanchu.entity.Mission;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/** 아이의 미션 제안. 미션 생성과 같은 필드인데 보상 대신 바라는 보상(desiredReward)을 받는다 */
public record MissionProposalRequest(
        @NotBlank @Size(max = 50) String title,
        @Size(max = 200) String criteria,
        @NotNull Mission.MissionType missionType,
        @NotNull @Positive Integer desiredReward,
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate,
        @NotNull Mission.RepeatType repeatType,
        @NotNull Mission.VerifyMethod verifyMethod
) {}
