package com.cheotdanchu.repository;

import com.cheotdanchu.entity.ChildMessage;
import jakarta.persistence.LockModeType;
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

    /** 부모 결정 카드: 아직 답하지 않은 전해진 말, 먼저 전한 순 */
    List<ChildMessage> findByFamilyIdAndStatusOrderBySentAtAscChildMessageIdAsc(Long familyId, ChildMessage.Status status);

    /** 월말 회고: 월말에 전하기로 한 말, 먼저 만든 순 */
    List<ChildMessage> findByFamilyIdAndStatusOrderByCreatedAtAscChildMessageIdAsc(Long familyId, ChildMessage.Status status);
}
