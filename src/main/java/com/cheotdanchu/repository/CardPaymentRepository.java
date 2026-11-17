package com.cheotdanchu.repository;

import com.cheotdanchu.entity.CardPayment;
import com.cheotdanchu.entity.Settlement;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public interface CardPaymentRepository extends JpaRepository<CardPayment, Long> {

    /** 청구 예정액: 정산 기간 결제 합계 (취소는 마이너스로 들어 있어 그대로 합산) */
    @Query("select coalesce(sum(p.amount), 0L) from CardPayment p where p.settlementId = :settlementId")
    long sumAmount(@Param("settlementId") Long settlementId);

    /** 정산의 청구액: 확정된 청구액이 있으면 그것, 아직 없는 정산(이번 달 등)은 카드 결제 합계 */
    default long billedOf(Settlement settlement) {
        return settlement.getBilledAmount() != null ? settlement.getBilledAmount() : sumAmount(settlement.getSettlementId());
    }

    /**
     * 결제 건수(취소된 결제는 뺀 순건수): 취소 행 하나가 원래 결제 하나를 지우는 것으로 보고, 취소가 아닌 행 수에서 취소 행 수를 뺀다.
     * 취소와 원래 결제를 잇는 값이 없어서 개수로 센다 (일부만 취소한 경우는 구분하지 못한다).
     */
    @Query("select coalesce(sum(case when p.canceled = true then -1L else 1L end), 0L) from CardPayment p where p.settlementId = :settlementId")
    long countNetPayments(@Param("settlementId") Long settlementId);

    /** 카테고리별 합계, 큰 금액순. 취소(마이너스)를 합산해서 전체 합이 청구 예정액과 같고, 합계가 0 이하가 된 카테고리는 뺀다. from 이상 to 미만 */
    @Query("""
            select p.category as category, sum(p.amount) as amount
            from CardPayment p
            where p.settlementId = :settlementId and p.memberId = :memberId and p.paidAt >= :from and p.paidAt < :to
            group by p.category
            having sum(p.amount) > 0
            order by sum(p.amount) desc, p.category
            """)
    List<CategoryTotal> sumByCategory(@Param("settlementId") Long settlementId, @Param("memberId") Long memberId,
                                      @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    interface CategoryTotal {
        String getCategory();

        Long getAmount();
    }

    /** 날짜별 합계(취소는 마이너스로 합산). 결제가 없는 날은 행이 없다. from 이상 to 미만 */
    @Query("""
            select cast(p.paidAt as date) as day, sum(p.amount) as amount
            from CardPayment p
            where p.settlementId = :settlementId and p.memberId = :memberId and p.paidAt >= :from and p.paidAt < :to
            group by cast(p.paidAt as date)
            """)
    List<DailyTotal> sumByDay(@Param("settlementId") Long settlementId, @Param("memberId") Long memberId,
                              @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    interface DailyTotal {
        LocalDate getDay();

        Long getAmount();
    }

    /** 고른 기간의 결제 수 (취소 건 포함, 페이지와 상관없는 전체) */
    long countBySettlementIdAndMemberIdAndPaidAtGreaterThanEqualAndPaidAtLessThan(
            Long settlementId, Long memberId, LocalDateTime from, LocalDateTime to);

    /**
     * 날짜순(최신 먼저) 한 페이지. 커서는 (결제 시각, 결제 ID) 로 이어 붙인다. 같은 시각이어도 ID 로 순서가 정해져서 건너뛰거나 겹치지 않는다.
     * 첫 페이지는 커서에 아주 먼 미래 값을 넣는다.
     */
    @Query("""
            select p from CardPayment p
            where p.settlementId = :settlementId and p.memberId = :memberId and p.paidAt >= :from and p.paidAt < :to
              and (p.paidAt < :cursorAt or (p.paidAt = :cursorAt and p.paymentId < :cursorId))
            order by p.paidAt desc, p.paymentId desc
            """)
    List<CardPayment> pageByDate(@Param("settlementId") Long settlementId, @Param("memberId") Long memberId,
                                 @Param("from") LocalDateTime from, @Param("to") LocalDateTime to,
                                 @Param("cursorAt") LocalDateTime cursorAt, @Param("cursorId") Long cursorId, Pageable pageable);

    /** 큰 금액순 한 페이지. 커서는 (금액, 결제 ID). 첫 페이지는 커서에 아주 큰 값을 넣는다 */
    @Query("""
            select p from CardPayment p
            where p.settlementId = :settlementId and p.memberId = :memberId and p.paidAt >= :from and p.paidAt < :to
              and (p.amount < :cursorAmount or (p.amount = :cursorAmount and p.paymentId < :cursorId))
            order by p.amount desc, p.paymentId desc
            """)
    List<CardPayment> pageByAmount(@Param("settlementId") Long settlementId, @Param("memberId") Long memberId,
                                   @Param("from") LocalDateTime from, @Param("to") LocalDateTime to,
                                   @Param("cursorAmount") int cursorAmount, @Param("cursorId") Long cursorId, Pageable pageable);
}
