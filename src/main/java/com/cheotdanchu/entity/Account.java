package com.cheotdanchu.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "accounts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Account {

    @Id
    private Long accountId;

    private Long memberId;

    /** 계좌 전체 잔액 (지난달까지 남은 돈 포함) */
    private int balance;

    /** 그중 지난달까지 남은 돈 */
    private int carryoverBalance;

    /** 이번 달 쓸 수 있는 돈으로 들어오는 입금 (용돈·미션 보상). 지난달까지 남은 돈은 그대로 */
    public void deposit(int amount) {
        balance += amount;
    }
}
