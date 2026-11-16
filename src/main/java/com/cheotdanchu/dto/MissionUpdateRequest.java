package com.cheotdanchu.dto;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/** 미션 수정: 바꿀 필드만 보낸다 (null이면 그대로) */
public record MissionUpdateRequest(
        @Size(max = 50) String title,
        @Size(max = 200) String criteria,
        @Positive Integer rewardAmount,
        LocalDate endDate
) {
    public boolean isEmpty() {
        return title == null && criteria == null && rewardAmount == null && endDate == null;
    }
}
