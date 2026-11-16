package com.cheotdanchu.dto;

import com.cheotdanchu.entity.Notification;

import java.time.LocalDateTime;
import java.util.List;

/** 알림 목록 응답 */
public final class NotificationDtos {

    private NotificationDtos() {}

    public record ListResponse(List<Item> notifications, Long nextCursor) {}

    public record Item(
            Long notificationId,
            Notification.Type type,
            String title,
            String body,
            Long refId,
            boolean isRead,
            LocalDateTime createdAt
    ) {}
}
