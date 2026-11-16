package com.cheotdanchu.repository;

import com.cheotdanchu.entity.Notification;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    /** 내 알림을 최신순으로. cursor는 이전 페이지 마지막 notificationId */
    List<Notification> findByMember_MemberIdAndNotificationIdLessThanOrderByNotificationIdDesc(
            Long memberId, Long cursor, Pageable pageable);

    long countByMember_MemberIdAndReadAtIsNull(Long memberId);
}
