package com.cheotdanchu.repository;

import com.cheotdanchu.entity.FamilySetting;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface FamilySettingRepository extends JpaRepository<FamilySetting, Long> {

    /** 용돈 저장처럼 가족 설정을 바꾸는 요청을 한 번에 하나씩만 처리하려고 행에 쓰기 잠금을 건다 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from FamilySetting s where s.familyId = :familyId")
    Optional<FamilySetting> findByIdForUpdate(@Param("familyId") Long familyId);

    /** 부모가 아이 결제를 어디까지 보는지. 가족 설정이 없으면 기본값(총액만). 자녀 홈·부모 홈이 같은 기본값을 쓴다 */
    default FamilySetting.Visibility visibilityOf(Long familyId) {
        return findById(familyId).map(FamilySetting::getVisibility).orElse(FamilySetting.Visibility.TOTAL_ONLY);
    }
}
