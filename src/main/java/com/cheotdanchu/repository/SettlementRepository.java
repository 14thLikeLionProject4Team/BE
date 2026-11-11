package com.cheotdanchu.repository;

import com.cheotdanchu.entity.Settlement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;

public interface SettlementRepository extends JpaRepository<Settlement, Long> {

    /** 기준일이 정산 기간(시작일~종료일) 안에 있는 정산 */
    Optional<Settlement> findByFamilyIdAndPeriodStartLessThanEqualAndPeriodEndGreaterThanEqual(
            Long familyId, LocalDate start, LocalDate end);

    /** 가족의 "현재 정산". 자녀 홈·부모 홈이 모두 이 메서드로 현재 정산을 정해서, 서로 다른 정산을 보는 일이 없게 한다 */
    default Optional<Settlement> findCurrent(Long familyId, LocalDate today) {
        return findByFamilyIdAndPeriodStartLessThanEqualAndPeriodEndGreaterThanEqual(familyId, today, today);
    }
}
