package com.cheotdanchu.repository;

import com.cheotdanchu.entity.Transfer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

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

    /** 정산에 연결된 이체 중 한 종류의 합계 (예: 지금까지 갚은 부족분) */
    @Query("select coalesce(sum(t.amount), 0L) from Transfer t where t.settlementId = :settlementId and t.type = :type")
    long sumBySettlementAndType(@Param("settlementId") Long settlementId, @Param("type") Transfer.Type type);

    /** 정산에 연결된 이체(결제일 정산·부족분 갚기·자동 정산)를 시간순으로 */
    List<Transfer> findBySettlementIdOrderByTransferredAtAscTransferIdAsc(Long settlementId);

    /** 미션 하나에 지금까지 지급한 보상 합계 */
    @Query("select coalesce(sum(t.amount), 0L) from Transfer t where t.missionId = :missionId and t.type = :type")
    long sumByMission(@Param("missionId") Long missionId, @Param("type") Transfer.Type type);
}
