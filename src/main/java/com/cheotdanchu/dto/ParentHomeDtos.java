package com.cheotdanchu.dto;

import com.cheotdanchu.entity.FamilySetting;

import java.time.LocalDate;
import java.util.List;

/** 부모 홈 응답 */
public final class ParentHomeDtos {

    private ParentHomeDtos() {}

    public record HomeResponse(
            LocalDate today,
            String childName,
            FamilySetting.Visibility visibility,
            String visibilityNotice,
            long unreadNotificationCount,
            List<String> praiseChips,
            boolean isAdultTransitioned
    ) {}
}
