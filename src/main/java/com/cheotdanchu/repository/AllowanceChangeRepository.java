package com.cheotdanchu.repository;

import com.cheotdanchu.entity.AllowanceChange;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Optional;

public interface AllowanceChangeRepository extends JpaRepository<AllowanceChange, Long> {

    /** 주어진 날짜에 적용 중인 용돈 (적용 시작일이 그날 이전인 변경 중 가장 최근) */
    Optional<AllowanceChange> findFirstByFamilyIdAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
            Long familyId, LocalDate date);

    /** 그 달 용돈 = 그 달 용돈 보내는 날에 적용 중인 금액. 보내는 날이 그 달에 없으면(31일 → 2월) 말일로 */
    default Optional<AllowanceChange> findForMonth(Long familyId, YearMonth month, int allowanceDay) {
        LocalDate date = month.atDay(Math.min(allowanceDay, month.lengthOfMonth()));
        return findFirstByFamilyIdAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(familyId, date);
    }
}
