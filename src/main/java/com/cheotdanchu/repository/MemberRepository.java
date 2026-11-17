package com.cheotdanchu.repository;

import com.cheotdanchu.common.exception.GeneralException;
import com.cheotdanchu.common.response.ErrorStatus;
import com.cheotdanchu.entity.Member;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MemberRepository extends JpaRepository<Member, Long> {

    Optional<Member> findByFamily_FamilyIdAndRole(Long familyId, Member.Role role);

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
}
