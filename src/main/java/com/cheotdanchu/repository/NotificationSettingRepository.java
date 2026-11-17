package com.cheotdanchu.repository;

import com.cheotdanchu.entity.NotificationItem;
import com.cheotdanchu.entity.NotificationSetting;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface NotificationSettingRepository extends JpaRepository<NotificationSetting, NotificationSetting.Key> {

    List<NotificationSetting> findByMemberId(Long memberId);

    Optional<NotificationSetting> findByMemberIdAndType(Long memberId, NotificationItem type);
}
