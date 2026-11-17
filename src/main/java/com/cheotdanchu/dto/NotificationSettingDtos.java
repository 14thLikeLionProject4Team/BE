package com.cheotdanchu.dto;

import com.cheotdanchu.entity.NotificationItem;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public final class NotificationSettingDtos {

    private NotificationSettingDtos() {}

    public record ListResponse(List<Item> items) {}

    public record Item(NotificationItem type, String label, boolean enabled) {}

    public record ChangeRequest(@NotNull Boolean enabled) {}

    public record ChangeResponse(NotificationItem type, boolean enabled) {}
}
