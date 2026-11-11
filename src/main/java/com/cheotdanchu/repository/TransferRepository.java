package com.cheotdanchu.repository;

import com.cheotdanchu.entity.Transfer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;

public interface TransferRepository extends JpaRepository<Transfer, Long> {

    /** 기간 안(from 이상, to 미만)에 해당 계좌로 들어온 이체가 있는지 */
    boolean existsByToAccountIdAndTypeAndTransferredAtGreaterThanEqualAndTransferredAtLessThan(
            Long toAccountId, Transfer.Type type, LocalDateTime from, LocalDateTime to);
}
