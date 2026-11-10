package com.cheotdanchu.repository;

import com.cheotdanchu.entity.Transfer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;

public interface TransferRepository extends JpaRepository<Transfer, Long> {

    /** 기간 안에 해당 계좌로 들어온 이체 합계 (from 이상, to 미만) */
    @Query("""
            select coalesce(sum(t.amount), 0L) from Transfer t
            where t.toAccountId = :accountId and t.type in :types
              and t.transferredAt >= :from and t.transferredAt < :to
            """)
    long sumReceived(@Param("accountId") Long accountId,
                     @Param("types") Collection<Transfer.Type> types,
                     @Param("from") LocalDateTime from,
                     @Param("to") LocalDateTime to);
}
