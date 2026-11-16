package com.cheotdanchu.service;

import com.cheotdanchu.dto.NotificationDtos;
import com.cheotdanchu.entity.Member;
import com.cheotdanchu.entity.Notification;
import com.cheotdanchu.repository.MemberRepository;
import com.cheotdanchu.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationService {

    private static final int PAGE_SIZE = 20;

    private final MemberRepository memberRepository;
    private final NotificationRepository notificationRepository;

    /** 내 알림 최신순 (자녀·부모 공용). 20개씩, cursor는 이전 페이지 마지막 notificationId */
    public NotificationDtos.ListResponse getNotifications(Long personaId, Long cursor) {
        Member member = memberRepository.getPersona(personaId);
        List<Notification> page = notificationRepository.findByMember_MemberIdAndNotificationIdLessThanOrderByNotificationIdDesc(
                member.getMemberId(), cursor == null ? Long.MAX_VALUE : cursor, PageRequest.of(0, PAGE_SIZE + 1));
        boolean hasNext = page.size() > PAGE_SIZE;
        List<NotificationDtos.Item> items = page.stream().limit(PAGE_SIZE)
                .map(n -> new NotificationDtos.Item(n.getNotificationId(), n.getType(), n.getTitle(), n.getBody(),
                        n.getRefId(), n.getReadAt() != null, n.getCreatedAt()))
                .toList();
        return new NotificationDtos.ListResponse(items, hasNext ? items.get(items.size() - 1).notificationId() : null);
    }
}
