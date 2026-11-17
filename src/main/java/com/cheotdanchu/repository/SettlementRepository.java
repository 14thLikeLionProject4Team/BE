package com.cheotdanchu.repository;

import com.cheotdanchu.entity.Settlement;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface SettlementRepository extends JpaRepository<Settlement, Long> {

    /** 기준일이 정산 기간(시작일~종료일) 안에 있는 정산 */
    Optional<Settlement> findByFamilyIdAndPeriodStartLessThanEqualAndPeriodEndGreaterThanEqual(
            Long familyId, LocalDate start, LocalDate end);

    /** 기록 지표에 들어가는 정산: 끝난 정산 중 제외되지 않은 것, 오래된 순 */
    List<Settlement> findByFamilyIdAndStatusAndExcludedFalseOrderByPeriodEndAsc(Long familyId, Settlement.Status status);

    /** 결제일 기록: 아직 정산 전(SCHEDULED)이 아닌 정산을 최신(종료일이 늦은 것)부터. before 보다 종료일이 이른 것만 (정산 종료일은 가족마다 하나씩이라 커서로 쓴다) */
    List<Settlement> findByFamilyIdAndStatusNotAndPeriodEndLessThanOrderByPeriodEndDesc(
            Long familyId, Settlement.Status excluded, LocalDate before, Pageable pageable);

    long countByFamilyIdAndStatusNot(Long familyId, Settlement.Status excluded);

    /** 가족의 "현재 정산". 자녀 홈·부모 홈이 모두 이 메서드로 현재 정산을 정해서, 서로 다른 정산을 보는 일이 없게 한다 */
    default Optional<Settlement> findCurrent(Long familyId, LocalDate today) {
        return findByFamilyIdAndPeriodStartLessThanEqualAndPeriodEndGreaterThanEqual(familyId, today, today);
    }
}
