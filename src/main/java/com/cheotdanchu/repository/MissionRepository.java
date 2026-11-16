package com.cheotdanchu.repository;

import com.cheotdanchu.entity.Mission;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface MissionRepository extends JpaRepository<Mission, Long> {

    /**
     * 상태를 바꾸는 요청(열기·보류·제출)에서 쓴다. 행에 쓰기 잠금을 걸어서 같은 미션을 동시에 두 번 처리해도(버튼 연타)
     * 뒤 요청은 앞 요청이 끝난 상태를 보고 막힌다. 잠금이 없으면 두 요청이 모두 상태 검사를 통과한다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from Mission m where m.missionId = :id")
    Optional<Mission> findByIdForUpdate(@Param("id") Long id);

    List<Mission> findByFamily_FamilyIdAndStatusInOrderByMissionId(Long familyId, Collection<Mission.Status> statuses);

    List<Mission> findByFamily_FamilyIdAndStatusInOrderByCreatedAtDesc(Long familyId, Collection<Mission.Status> statuses);

    /** 커서(이전 페이지 마지막 missionId)보다 작은 것부터 최신순 */
    List<Mission> findByFamily_FamilyIdAndStatusInAndMissionIdLessThanOrderByMissionIdDesc(
            Long familyId, Collection<Mission.Status> statuses, Long cursor, Pageable pageable);
}
