package com.cheotdanchu.repository;

import com.cheotdanchu.entity.ChildMessage;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ChildMessageRepository extends JpaRepository<ChildMessage, Long> {

    /** 자녀와 부모가 같은 말을 동시에 바꾸지 않도록 잠그고 읽는다 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from ChildMessage m where m.childMessageId = :id")
    Optional<ChildMessage> findByIdForUpdate(@Param("id") Long id);

    /** 가족의 특정 상태 말. 정렬은 쓰는 쪽이 정한다 (결정 카드는 전한 순, 월말 회고는 만든 순) */
    List<ChildMessage> findByFamilyIdAndStatus(Long familyId, ChildMessage.Status status, Sort sort);

    /** 월말 회고 승인: 같은 회고를 두 번 보내도 한 번만 처리되도록 잠그고 읽는다 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from ChildMessage m where m.familyId = :familyId and m.status = :status")
    List<ChildMessage> findByFamilyIdAndStatusForUpdate(@Param("familyId") Long familyId, @Param("status") ChildMessage.Status status);
}
