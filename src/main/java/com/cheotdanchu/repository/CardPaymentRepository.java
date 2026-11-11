package com.cheotdanchu.repository;

import com.cheotdanchu.entity.CardPayment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CardPaymentRepository extends JpaRepository<CardPayment, Long> {

    /** 청구 예정액: 정산 기간 결제 합계 (취소는 마이너스로 들어 있어 그대로 합산) */
    @Query("select coalesce(sum(p.amount), 0L) from CardPayment p where p.settlementId = :settlementId")
    long sumAmount(@Param("settlementId") Long settlementId);

    /** 결제 건수: 취소 건은 제외 */
    long countBySettlementIdAndCanceledFalse(Long settlementId);
}
