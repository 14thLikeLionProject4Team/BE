package com.cheotdanchu.repository;

import com.cheotdanchu.entity.MissionSubmission;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MissionSubmissionRepository extends JpaRepository<MissionSubmission, Long> {

    long countByMission_MissionIdAndReviewStatus(Long missionId, MissionSubmission.ReviewStatus reviewStatus);

    List<MissionSubmission> findByMission_MissionIdOrderBySubmittedAtDesc(Long missionId);
}
