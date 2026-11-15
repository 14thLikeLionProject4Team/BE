package com.cheotdanchu.repository;

import com.cheotdanchu.entity.MissionSubmission;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MissionSubmissionRepository extends JpaRepository<MissionSubmission, Long> {

    long countByMission_MissionIdAndReviewStatus(Long missionId, MissionSubmission.ReviewStatus reviewStatus);

    List<MissionSubmission> findByMission_MissionIdOrderBySubmittedAtDesc(Long missionId);

    List<MissionSubmission> findByMission_Family_FamilyIdAndReviewStatusOrderBySubmittedAtDesc(
            Long familyId, MissionSubmission.ReviewStatus reviewStatus);

    /** 마지막으로 승인된 제출 (끝난 미션의 끝난 날로 쓴다) */
    Optional<MissionSubmission> findFirstByMission_MissionIdAndReviewStatusOrderByReviewedAtDesc(
            Long missionId, MissionSubmission.ReviewStatus reviewStatus);
}
