package com.cheotdanchu.service;

import com.cheotdanchu.common.exception.GeneralException;
import com.cheotdanchu.common.response.ErrorStatus;
import com.cheotdanchu.dto.NotificationSettingDtos;
import com.cheotdanchu.entity.Member;
import com.cheotdanchu.entity.Notification;
import com.cheotdanchu.entity.NotificationItem;
import com.cheotdanchu.entity.NotificationSetting;
import com.cheotdanchu.repository.MemberRepository;
import com.cheotdanchu.repository.NotificationSettingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.stream.Collectors;

/** 알림 설정(마이): 부모·자녀가 각자 자기 알림 항목을 켜고 끈다. 바꾼 적 없는 항목은 NotificationItem 의 기본값이다 */
@Service
@RequiredArgsConstructor
public class NotificationSettingService {

    private final MemberRepository memberRepository;
    private final NotificationSettingRepository settingRepository;

    /** 내 알림 항목과 켜짐 여부(항목 종류와 기본값은 부모·자녀에 따라 다르다) */
    @Transactional(readOnly = true)
    public NotificationSettingDtos.ListResponse getSettings(Long personaId) {
        Member me = memberRepository.getPersona(personaId);
        Map<NotificationItem, Boolean> saved = settingRepository.findByMemberId(me.getMemberId()).stream()
                .collect(Collectors.toMap(NotificationSetting::getType, NotificationSetting::isEnabled));

        return new NotificationSettingDtos.ListResponse(NotificationItem.of(me.getRole()).stream()
                .map(item -> new NotificationSettingDtos.Item(item, item.getLabel(), saved.getOrDefault(item, item.defaultFor(me.getRole()))))
                .toList());
    }

    /**
     * 내 알림 항목 하나를 켜고 끈다. 내 알림만 바꿀 수 있어서, 없는 항목이거나 내 역할에 없는 항목이면 404 다.
     * 처음 바꾸는 항목은 행을 새로 만드는데, 같은 사람의 요청이 동시에 와도 키 중복이 나지 않게 구성원 행에 잠금을 걸고,
     * 잠금이 풀린 뒤에는 앞 요청이 저장한 행이 보여야 해서 READ_COMMITTED 로 읽는다.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public NotificationSettingDtos.ChangeResponse update(Long personaId, String typeName, boolean enabled) {
        Member me = memberRepository.getPersona(personaId);
        NotificationItem item = NotificationItem.of(me.getRole()).stream()
                .filter(i -> i.name().equals(typeName))
                .findFirst()
                .orElseThrow(() -> new GeneralException(ErrorStatus.NOTIFICATION_TYPE_NOT_FOUND));

        memberRepository.findByIdForUpdate(me.getMemberId());
        settingRepository.findByMemberIdAndType(me.getMemberId(), item)
                .ifPresentOrElse(setting -> setting.changeEnabled(enabled),
                        () -> settingRepository.save(NotificationSetting.of(me.getMemberId(), item, enabled)));
        return new NotificationSettingDtos.ChangeResponse(item, enabled);
    }

    /** 이 알림을 받는 사람의 설정이 켜져 있는지. 설정 항목이 없는 알림이거나 그 역할의 항목이 아니면 항상 보낸다 */
    @Transactional(readOnly = true)
    public boolean isEnabled(Member receiver, Notification.Type type) {
        return NotificationItem.coveredBy(type)
                .filter(item -> item.isFor(receiver.getRole()))
                .map(item -> settingRepository.findByMemberIdAndType(receiver.getMemberId(), item)
                        .map(NotificationSetting::isEnabled)
                        .orElse(item.defaultFor(receiver.getRole())))
                .orElse(true);
    }
}
