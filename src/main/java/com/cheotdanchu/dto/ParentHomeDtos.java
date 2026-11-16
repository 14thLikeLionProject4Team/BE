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

    public record DecisionsResponse(int count, List<DecisionCard> cards) {}

    /** refId: 용돈 제안이면 suggestionId, 전할 말이면 childMessageId, 미션 확인이면 submissionId */
    public record DecisionCard(CardType type, Long refId, String title, String body, String label) {}

    public enum CardType { ALLOWANCE_SUGGESTION, CHILD_MESSAGE, MISSION_REVIEW }
}
