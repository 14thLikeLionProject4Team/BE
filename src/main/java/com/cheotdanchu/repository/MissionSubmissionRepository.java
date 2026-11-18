package com.cheotdanchu.repository;

import com.cheotdanchu.entity.MissionSubmission;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MissionSubmissionRepository extends JpaRepository<MissionSubmission, Long> {

    /** 승인·반려에서 쓴다. 같은 제출을 동시에 두 번 승인해 보상이 두 번 나가는 일을 막으려고 행에 쓰기 잠금을 건다 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from MissionSubmission s where s.submissionId = :id")
    Optional<MissionSubmission> findByIdForUpdate(@Param("id") Long id);

    long countByMission_MissionIdAndReviewStatus(Long missionId, MissionSubmission.ReviewStatus reviewStatus);

    List<MissionSubmission> findByMission_MissionIdOrderBySubmittedAtDesc(Long missionId);

    List<MissionSubmission> findByMission_Family_FamilyIdAndReviewStatusOrderBySubmittedAtDesc(
            Long familyId, MissionSubmission.ReviewStatus reviewStatus);

    /** 마지막으로 승인된 제출 (끝난 미션의 끝난 날로 쓴다) */
    Optional<MissionSubmission> findFirstByMission_MissionIdAndReviewStatusOrderByReviewedAtDesc(
            Long missionId, MissionSubmission.ReviewStatus reviewStatus);
}
