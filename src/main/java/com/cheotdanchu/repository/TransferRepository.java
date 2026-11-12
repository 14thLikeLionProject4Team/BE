package com.cheotdanchu.repository;

import com.cheotdanchu.entity.Transfer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface TransferRepository extends JpaRepository<Transfer, Long> {

    /** 기간 안(from 이상, to 미만)에 해당 계좌로 들어온 이체가 있는지 */
    boolean existsByToAccountIdAndTypeAndTransferredAtGreaterThanEqualAndTransferredAtLessThan(
            Long toAccountId, Transfer.Type type, LocalDateTime from, LocalDateTime to);

    /** 기간 안(from 이상, to 미만)에 해당 계좌로 들어온 이체 합계 */
    @Query("""
            select coalesce(sum(t.amount), 0L) from Transfer t
            where t.toAccountId = :accountId and t.type = :type
              and t.transferredAt >= :from and t.transferredAt < :to
            """)
    long sumReceived(@Param("accountId") Long accountId, @Param("type") Transfer.Type type,
                     @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);
}
