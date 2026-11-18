package com.cheotdanchu.repository;

import com.cheotdanchu.entity.Account;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface AccountRepository extends JpaRepository<Account, Long> {

    Optional<Account> findByMemberId(Long memberId);

    /** 계좌 잔액을 바꾸는 요청(부족분 갚기)에서 쓴다. 행에 쓰기 잠금을 걸어서 같은 계좌의 동시 요청이 한 줄로 처리되게 한다 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Account a where a.memberId = :memberId")
    Optional<Account> findByMemberIdForUpdate(@Param("memberId") Long memberId);
}
