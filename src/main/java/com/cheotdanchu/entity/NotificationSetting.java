package com.cheotdanchu.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/** 구성원이 바꾼 알림 설정. 바꾼 적 없는 항목은 행이 없고 NotificationItem 의 기본값을 따른다 */
@Entity
@Table(name = "notification_settings")
@IdClass(NotificationSetting.Key.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NotificationSetting {

    @Id
    private Long memberId;

    @Id
    @Enumerated(EnumType.STRING)
    private NotificationItem type;

    private boolean enabled;

    public static NotificationSetting of(Long memberId, NotificationItem type, boolean enabled) {
        NotificationSetting s = new NotificationSetting();
        s.memberId = memberId;
        s.type = type;
        s.enabled = enabled;
        return s;
    }

    public void changeEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    @NoArgsConstructor
    @EqualsAndHashCode
    public static class Key implements Serializable {
        private Long memberId;
        private NotificationItem type;
    }
}
