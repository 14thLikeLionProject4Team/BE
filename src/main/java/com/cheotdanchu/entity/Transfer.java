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
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long transferId;

    private Long fromAccountId;

    private Long toAccountId;

    @Enumerated(EnumType.STRING)
    private Type type;

    private int amount;

    private Long missionId;

    private LocalDateTime transferredAt;

    public static Transfer missionReward(Long fromAccountId, Long toAccountId, int amount, Long missionId, LocalDateTime at) {
        Transfer t = new Transfer();
        t.fromAccountId = fromAccountId;
        t.toAccountId = toAccountId;
        t.type = Type.MISSION_REWARD;
        t.amount = amount;
        t.missionId = missionId;
        t.transferredAt = at;
        return t;
    }

    public enum Type { ALLOWANCE, SETTLEMENT, SHORTFALL_PAYMENT, AUTO_DEDUCT, MISSION_REWARD, PARENT_SUPPORT }
}
