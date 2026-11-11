package com.cheotdanchu.repository;

import com.cheotdanchu.common.exception.GeneralException;
import com.cheotdanchu.common.response.ErrorStatus;
import com.cheotdanchu.entity.Member;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberRepository extends JpaRepository<Member, Long> {

    /** X-Persona-Id로 구성원을 찾는다. 없으면 404 */
    default Member getPersona(Long personaId) {
        return findById(personaId).orElseThrow(() -> new GeneralException(ErrorStatus.PERSONA_NOT_FOUND));
    }
}
