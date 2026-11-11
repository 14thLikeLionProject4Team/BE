package com.cheotdanchu.repository;

import com.cheotdanchu.entity.AllowanceChange;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;

public interface AllowanceChangeRepository extends JpaRepository<AllowanceChange, Long> {

    /** 주어진 날짜에 적용 중인 용돈 (적용 시작일이 그날 이전인 변경 중 가장 최근) */
    Optional<AllowanceChange> findFirstByFamilyIdAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
            Long familyId, LocalDate date);
}
