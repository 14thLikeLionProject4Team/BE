package com.cheotdanchu.repository;

import com.cheotdanchu.entity.Briefing;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BriefingRepository extends JpaRepository<Briefing, Long> {

    Optional<Briefing> findFirstByFamilyIdOrderByTargetMonthDesc(Long familyId);

    /** 지난 브리핑 최근 달 순. cursor는 이전 페이지 마지막 대상 달 (YYYY-MM이라 글자 순서 = 달 순서) */
    List<Briefing> findByFamilyIdAndTargetMonthLessThanOrderByTargetMonthDesc(Long familyId, String cursor, Pageable pageable);

    long countByFamilyId(Long familyId);
}
