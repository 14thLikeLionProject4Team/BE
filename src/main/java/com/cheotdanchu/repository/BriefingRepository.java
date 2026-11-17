package com.cheotdanchu.repository;

import com.cheotdanchu.entity.Briefing;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BriefingRepository extends JpaRepository<Briefing, Long> {

    Optional<Briefing> findFirstByFamilyIdOrderByTargetMonthDesc(Long familyId);
}
