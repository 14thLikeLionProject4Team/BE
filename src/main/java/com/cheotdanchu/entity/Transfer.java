package com.cheotdanchu.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** 이체 내역. 잔액(accounts.balance)은 이 합계로 구하지 않고 따로 갱신한다 (이체는 기록용) */
@Entity
@Table(name = "transfers")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Transfer {

    @Id
    private Long transferId;

    private Long toAccountId;

    @Enumerated(EnumType.STRING)
    private Type type;

    private int amount;

    private LocalDateTime transferredAt;

    public enum Type { ALLOWANCE, SETTLEMENT, SHORTFALL_PAYMENT, AUTO_DEDUCT, MISSION_REWARD, PARENT_SUPPORT }
}
