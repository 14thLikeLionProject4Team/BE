package com.cheotdanchu.dto;

import java.time.LocalDate;
import java.util.List;

public record ShortfallCheckResponse(
        boolean visible,
        LocalDate dueDate,   // 진행 중인 정산이 없으면 null
        Long dDay,           // 진행 중인 정산이 없으면 null
        long billedAmount,
        long availableAmount,
        long carryoverAmount,
        long shortfallAmount,
        String message,
        List<String> guides
) {}
