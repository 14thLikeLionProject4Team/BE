package com.cheotdanchu.repository;

import com.cheotdanchu.entity.AllowanceChange;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.time.YearMonth;
import java.util.Optional;

public interface AllowanceChangeRepository extends JpaRepository<AllowanceChange, Long> {

    /** 주어진 날짜에 적용 중인 용돈 (적용 시작일이 그날 이전인 변경 중 가장 최근. 시작일이 같으면 나중에 저장한 것) */
    Optional<AllowanceChange> findFirstByFamilyIdAndEffectiveFromLessThanEqualOrderByEffectiveFromDescChangeIdDesc(
            Long familyId, LocalDate date);

    /** 그 달 용돈 = 그 달 용돈 보내는 날에 적용 중인 금액. 보내는 날이 그 달에 없으면(31일 → 2월) 말일로 */
    default Optional<AllowanceChange> findForMonth(Long familyId, YearMonth month, int allowanceDay) {
        LocalDate date = month.atDay(Math.min(allowanceDay, month.lengthOfMonth()));
        return findFirstByFamilyIdAndEffectiveFromLessThanEqualOrderByEffectiveFromDescChangeIdDesc(familyId, date);
    }

    /** 이력 한 페이지(최신순). 커서는 (만든 시각, ID) 라 같은 시각이어도 순서가 정해져서 건너뛰거나 겹치지 않는다. 첫 페이지는 커서에 아주 먼 미래 값을 넣는다 */
    @Query("""
            select c from AllowanceChange c
            where c.familyId = :familyId and (c.createdAt < :at or (c.createdAt = :at and c.changeId < :id))
            order by c.createdAt desc, c.changeId desc
            """)
    List<AllowanceChange> page(@Param("familyId") Long familyId, @Param("at") LocalDateTime at, @Param("id") Long id, Pageable pageable);

    long countByFamilyIdAndReason(Long familyId, AllowanceChange.Reason reason);
}
