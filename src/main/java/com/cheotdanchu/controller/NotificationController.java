package com.cheotdanchu.controller;

import com.cheotdanchu.common.response.ApiResponse;
import com.cheotdanchu.common.response.SuccessStatus;
import com.cheotdanchu.dto.NotificationDtos;
import com.cheotdanchu.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @Operation(summary = "알림 목록", description = "내 알림 최신순, 20개씩. cursor는 이전 페이지의 nextCursor. 자녀·부모 공용.")
    @GetMapping("/notifications")
    public ApiResponse<NotificationDtos.ListResponse> getNotifications(
            @RequestHeader("X-Persona-Id") Long personaId,
            @RequestParam(required = false) Long cursor) {
        return ApiResponse.onSuccess(SuccessStatus.NOTIFICATION_LIST_OK, notificationService.getNotifications(personaId, cursor));
    }
}
