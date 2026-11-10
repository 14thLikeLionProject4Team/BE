package com.cheotdanchu.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "transfers")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Transfer {

    @Id
    private Long transferId;

    private Long fromAccountId;
    private Long toAccountId;

    @Enumerated(EnumType.STRING)
    private Type type;

    private int amount;

    private LocalDateTime transferredAt;

    public enum Type { ALLOWANCE, SETTLEMENT, SHORTFALL_PAYMENT, AUTO_DEDUCT, MISSION_REWARD, PARENT_SUPPORT }
}
