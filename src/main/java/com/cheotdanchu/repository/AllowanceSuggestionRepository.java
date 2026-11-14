package com.cheotdanchu.repository;

import com.cheotdanchu.entity.AllowanceSuggestion;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface AllowanceSuggestionRepository extends JpaRepository<AllowanceSuggestion, Long> {

    /** 가족의 대기 중인 제안 (가장 최근 것) */
    Optional<AllowanceSuggestion> findFirstByFamilyIdAndStatusOrderByCreatedAtDesc(Long familyId, AllowanceSuggestion.Status status);

    /**
     * 처리할 때 쓴다. 행에 쓰기 잠금을 걸어서, 같은 제안을 동시에 두 번 처리해도(버튼 연타) 한 요청만 통과하게 한다.
     * 잠금이 없으면 두 요청이 모두 "대기 중"으로 보고 용돈 변경 이력을 두 번 남긴다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from AllowanceSuggestion s where s.suggestionId = :id")
    Optional<AllowanceSuggestion> findByIdForUpdate(@Param("id") Long id);
}
