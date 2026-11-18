package com.cheotdanchu.service;

import com.cheotdanchu.ai.SeedRepository;
import com.cheotdanchu.common.exception.GeneralException;
import com.cheotdanchu.common.response.ErrorStatus;
import com.cheotdanchu.dto.NotificationDtos;
import com.cheotdanchu.entity.Member;
import com.cheotdanchu.entity.Notification;
import com.cheotdanchu.repository.MemberRepository;
import com.cheotdanchu.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationService {

    private static final int PAGE_SIZE = 20;

    private final MemberRepository memberRepository;
    private final NotificationRepository notificationRepository;
    private final SeedRepository seedRepository;

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

    /** 내 알림만 읽음 처리한다. 다른 사람 알림은 존재 여부를 알려주지 않으려고 404. 다시 읽어도 성공 */
    @Transactional
    public void markRead(Long personaId, Long notificationId) {
        Member member = memberRepository.getPersona(personaId);
        Notification notification = notificationRepository.findById(notificationId)
                .filter(n -> n.getMember().getMemberId().equals(member.getMemberId()))
                .orElseThrow(() -> new GeneralException(ErrorStatus.NOTIFICATION_NOT_FOUND));
        // 다른 시각(이체·알림)과 맞춰 데모 기준일 + 지금 시각으로 남긴다
        notification.markRead(seedRepository.today().atTime(LocalTime.now().withNano(0)));
    }
}
