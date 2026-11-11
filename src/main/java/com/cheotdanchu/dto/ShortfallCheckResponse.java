package com.cheotdanchu.dto;

import java.time.LocalDate;
import java.util.List;

public record ShortfallCheckResponse(
        boolean visible,
        LocalDate dueDate,
        long dDay,
        long billedAmount,
        long availableAmount,
        long carryoverAmount,
        long shortfallAmount,
        String message,
        List<String> guides
) {}
