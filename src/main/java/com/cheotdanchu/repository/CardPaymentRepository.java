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

    /**
     * 결제 건수(취소된 결제는 뺀 순건수): 취소 행 하나가 원래 결제 하나를 지우는 것으로 보고, 취소가 아닌 행 수에서 취소 행 수를 뺀다.
     * 취소와 원래 결제를 잇는 값이 없어서 개수로 센다 (일부만 취소한 경우는 구분하지 못한다).
     */
    @Query("select coalesce(sum(case when p.canceled = true then -1L else 1L end), 0L) from CardPayment p where p.settlementId = :settlementId")
    long countNetPayments(@Param("settlementId") Long settlementId);

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
