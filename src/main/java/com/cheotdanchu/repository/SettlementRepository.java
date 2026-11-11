package com.cheotdanchu.repository;

import com.cheotdanchu.entity.Settlement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;

public interface SettlementRepository extends JpaRepository<Settlement, Long> {

    /** 기준일이 정산 기간(시작일~종료일) 안에 있는 정산 */
    Optional<Settlement> findByFamilyIdAndPeriodStartLessThanEqualAndPeriodEndGreaterThanEqual(
            Long familyId, LocalDate start, LocalDate end);
}
