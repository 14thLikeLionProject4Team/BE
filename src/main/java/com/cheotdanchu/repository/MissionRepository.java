package com.cheotdanchu.repository;

import com.cheotdanchu.entity.Mission;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface MissionRepository extends JpaRepository<Mission, Long> {

    List<Mission> findByFamily_FamilyIdAndStatusInOrderByMissionId(Long familyId, Collection<Mission.Status> statuses);
}
