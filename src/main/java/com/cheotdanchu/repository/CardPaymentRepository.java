package com.cheotdanchu.repository;

import com.cheotdanchu.entity.CardPayment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface CardPaymentRepository extends JpaRepository<CardPayment, Long> {

    /** 청구 예정액: 정산 기간 결제 합계 (취소는 마이너스로 들어 있어 그대로 합산) */
    @Query("select coalesce(sum(p.amount), 0L) from CardPayment p where p.settlementId = :settlementId")
    long sumAmount(@Param("settlementId") Long settlementId);

    /** 결제 건수: 취소 건은 제외 */
    long countBySettlementIdAndCanceledFalse(Long settlementId);

    /** 카테고리별 합계. 취소(마이너스)를 그대로 합산해서, 전체 합이 청구 예정액과 같다. from 이상 to 미만 */
    @Query("""
            select p.category as category, sum(p.amount) as amount
            from CardPayment p
            where p.settlementId = :settlementId and p.memberId = :memberId and p.paidAt >= :from and p.paidAt < :to
            group by p.category
            """)
    List<CategoryTotal> sumByCategory(@Param("settlementId") Long settlementId, @Param("memberId") Long memberId,
                                      @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    interface CategoryTotal {
        String getCategory();

        Long getAmount();
    }
}
