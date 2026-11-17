package com.cheotdanchu.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "card_payments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CardPayment {

    @Id
    private Long paymentId;

    private Long memberId;

    private Long settlementId;

    /** 카테고리 (식사·교통·쇼핑 등) */
    private String category;

    /** 취소 결제는 마이너스 금액 */
    private int amount;

    @Column(name = "is_canceled")
    private boolean canceled;

    private LocalDateTime paidAt;
}
