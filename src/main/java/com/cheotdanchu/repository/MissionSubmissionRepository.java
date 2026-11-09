package com.cheotdanchu.repository;

import com.cheotdanchu.entity.MissionSubmission;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MissionSubmissionRepository extends JpaRepository<MissionSubmission, Long> {

    long countByMission_MissionIdAndReviewStatus(Long missionId, MissionSubmission.ReviewStatus reviewStatus);
}
