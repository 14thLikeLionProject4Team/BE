package com.cheotdanchu.repository;

import com.cheotdanchu.entity.Mission;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface MissionRepository extends JpaRepository<Mission, Long> {

    List<Mission> findByFamily_FamilyIdAndStatusInOrderByMissionId(Long familyId, Collection<Mission.Status> statuses);

    List<Mission> findByFamily_FamilyIdAndStatusInOrderByCreatedAtDesc(Long familyId, Collection<Mission.Status> statuses);

    /** 커서(이전 페이지 마지막 missionId)보다 작은 것부터 최신순 */
    List<Mission> findByFamily_FamilyIdAndStatusInAndMissionIdLessThanOrderByMissionIdDesc(
            Long familyId, Collection<Mission.Status> statuses, Long cursor, Pageable pageable);
}
