package com.cheotdanchu.repository;

import com.cheotdanchu.common.exception.GeneralException;
import com.cheotdanchu.common.response.ErrorStatus;
import com.cheotdanchu.entity.Member;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface MemberRepository extends JpaRepository<Member, Long> {

    Optional<Member> findByFamily_FamilyIdAndRole(Long familyId, Member.Role role);

    /** 같은 구성원의 요청이 한 줄로 처리되게(확인서 발급 연타 방지) 행에 쓰기 잠금을 건다 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from Member m where m.memberId = :id")
    Optional<Member> findByIdForUpdate(@Param("id") Long id);

    /** X-Persona-Id로 구성원을 찾는다. 없으면 404 */
    default Member getPersona(Long personaId) {
        return findById(personaId).orElseThrow(() -> new GeneralException(ErrorStatus.PERSONA_NOT_FOUND));
    }

    /** 자녀 전용 API에서 쓴다. 자녀가 아니면 403 */
    default Member getChild(Long personaId) {
        Member member = getPersona(personaId);
        if (member.getRole() != Member.Role.CHILD) throw new GeneralException(ErrorStatus.CHILD_ONLY);
        return member;
    }

    /**
     * 부모가 고른 우리 가족의 아이. childId 를 안 주면 첫째 아이(지금은 아이 1명)이고,
     * 다른 가족의 아이거나 아이가 아니면 없는 것과 같이 404 다.
     */
    default Member getChildOfFamily(Member parent, Long childId) {
        Long familyId = parent.getFamily().getFamilyId();
        Optional<Member> child = childId == null
                ? findByFamily_FamilyIdAndRole(familyId, Member.Role.CHILD)
                : findById(childId).filter(m -> m.getRole() == Member.Role.CHILD && m.getFamily().getFamilyId().equals(familyId));
        return child.orElseThrow(() -> new GeneralException(ErrorStatus.PERSONA_NOT_FOUND));
    }

    /** 부모 전용 API에서 쓴다. 부모가 아니면 403 */
    default Member getParent(Long personaId) {
        Member member = getPersona(personaId);
        if (member.getRole() != Member.Role.PARENT) throw new GeneralException(ErrorStatus.PARENT_ONLY);
        return member;
    }
}
