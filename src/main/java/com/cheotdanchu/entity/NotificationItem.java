package com.cheotdanchu.entity;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * 알림 설정 항목. 항목 하나가 알림 종류(Notification.Type) 여러 개를 묶을 수 있다
 * (결제일 정산 결과 항목 하나가 정산 결과·부족분 해소·자동 정산 알림을 함께 켜고 끈다).
 * 항목은 부모·자녀 중 해당하는 쪽에만 있고, 기본값은 역할마다 다를 수 있다(부족 예상은 자녀 켜짐, 부모 꺼짐).
 * 선언 순서가 화면에 보이는 순서다(부모·자녀 목록 모두 이 순서에서 자기 항목만 고른다).
 */
public enum NotificationItem {

    SETTLEMENT_RESULT("결제일 정산 결과", true, true),
    SHORTFALL_EXPECTED("부족 예상", true, false),
    ALLOWANCE_CHANGED("용돈 변경", true, null),
    NEW_MISSION("새 미션", true, null),
    MISSION_REWARD("미션 보상", true, null),
    MISSION_REJECTED("미션 반려", true, null),
    MISSION_PROPOSED("미션 제안", null, true),
    MISSION_REMIND("미션 확인 재요청", null, true),
    RISK_PAYMENT("위험 결제", null, true),
    ADULT_TRANSITION("성년 전환", null, true);

    private final String label;
    private final Boolean childDefault;   // 자녀에게 없는 항목이면 null
    private final Boolean parentDefault;  // 부모에게 없는 항목이면 null

    NotificationItem(String label, Boolean childDefault, Boolean parentDefault) {
        this.label = label;
        this.childDefault = childDefault;
        this.parentDefault = parentDefault;
    }

    public String getLabel() {
        return label;
    }

    public boolean isFor(Member.Role role) {
        return defaultFor(role) != null;
    }

    /** 그 역할의 기본 켜짐 여부. 그 역할에 없는 항목이면 null */
    public Boolean defaultFor(Member.Role role) {
        return role == Member.Role.CHILD ? childDefault : parentDefault;
    }

    /** 그 역할이 설정할 수 있는 항목들(화면 순서) */
    public static List<NotificationItem> of(Member.Role role) {
        return Arrays.stream(values()).filter(item -> item.isFor(role)).toList();
    }

    /** 이 알림 종류를 켜고 끄는 항목. 설정 항목이 없는 알림이면 empty (항상 보낸다) */
    public static Optional<NotificationItem> coveredBy(Notification.Type type) {
        return switch (type) {
            case SETTLEMENT_RESULT, SHORTFALL_RESOLVED, AUTO_DEDUCT -> Optional.of(SETTLEMENT_RESULT);
            default -> Arrays.stream(values()).filter(item -> item.name().equals(type.name())).findFirst();
        };
    }
}
