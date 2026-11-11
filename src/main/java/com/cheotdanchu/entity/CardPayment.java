package com.cheotdanchu.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "card_payments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CardPayment {

    @Id
    private Long paymentId;

    private Long settlementId;

    /** 취소 결제는 마이너스 금액 */
    private int amount;

    @Column(name = "is_canceled")
    private boolean canceled;
}
