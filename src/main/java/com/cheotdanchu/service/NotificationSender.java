package com.cheotdanchu.service;

import com.cheotdanchu.entity.Notification;
import com.cheotdanchu.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 알림을 만드는 모든 곳이 거치는 문. 받는 사람이 그 알림 항목을 꺼 두었으면 만들지 않는다 */
@Component
@RequiredArgsConstructor
public class NotificationSender {

    private final NotificationRepository notificationRepository;
    private final NotificationSettingService settingService;

    /** 설정이 켜져 있어서 저장했으면 true */
    public boolean send(Notification notification) {
        if (!settingService.isEnabled(notification.getMember(), notification.getType())) return false;
        notificationRepository.save(notification);
        return true;
    }
}
