package com.cheotdanchu.dto;

import com.cheotdanchu.entity.Settlement;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record CurrentSettlementResponse(
        Long settlementId,
        LocalDate periodStart,
        LocalDate periodEnd,
        LocalDateTime dueAt,
        long dDay,
        Settlement.Status status,
        long billedAmount,
        long paymentCount,
        long availableAmount,
        long carryoverAmount,
        long shortfallAmount,
        long surplusAmount,
        boolean cardLinked
) {}
