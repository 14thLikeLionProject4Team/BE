package com.cheotdanchu.repository;

import com.cheotdanchu.entity.Briefing;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BriefingRepository extends JpaRepository<Briefing, Long> {

    Optional<Briefing> findFirstByFamilyIdOrderByTargetMonthDesc(Long familyId);

    /** 지난 브리핑 최신순 (매달 1일에 하나씩 만들어서 아이디 순서 = 달 순서). cursor는 이전 페이지 마지막 briefingId */
    List<Briefing> findByFamilyIdAndBriefingIdLessThanOrderByBriefingIdDesc(Long familyId, Long cursor, Pageable pageable);

    long countByFamilyId(Long familyId);
}
