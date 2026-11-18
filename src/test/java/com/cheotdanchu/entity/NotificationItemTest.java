package com.cheotdanchu.entity;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

class NotificationItemTest {

    @Test
    void 결제일_정산_결과_항목_하나가_정산_결과_부족분_해소_자동_정산_알림을_묶는다() {
        assertEquals(Optional.of(NotificationItem.SETTLEMENT_RESULT), NotificationItem.coveredBy(Notification.Type.SETTLEMENT_RESULT));
        assertEquals(Optional.of(NotificationItem.SETTLEMENT_RESULT), NotificationItem.coveredBy(Notification.Type.SHORTFALL_RESOLVED));
        assertEquals(Optional.of(NotificationItem.SETTLEMENT_RESULT), NotificationItem.coveredBy(Notification.Type.AUTO_DEDUCT));
    }

    @Test
    void 나머지_알림은_같은_이름의_항목이_켜고_끈다() {
        for (Notification.Type type : Notification.Type.values()) {
            if (NotificationItem.SETTLEMENT_RESULT.equals(NotificationItem.coveredBy(type).orElse(null))) continue; // 위에서 확인
            assertEquals(type.name(), NotificationItem.coveredBy(type).orElseThrow().name());
        }
    }

    @Test
    void 알림_종류마다_설정_항목이_있다() {
        for (Notification.Type type : Notification.Type.values()) {
            assertFalse(NotificationItem.coveredBy(type).isEmpty(), type + " 에 설정 항목이 없어요");
        }
    }

    @Test
    void 역할마다_항목과_기본값이_다르다() {
        assertEquals(6, NotificationItem.of(Member.Role.CHILD).size());
        assertEquals(6, NotificationItem.of(Member.Role.PARENT).size());
        assertEquals(true, NotificationItem.SHORTFALL_EXPECTED.defaultFor(Member.Role.CHILD));
        assertEquals(false, NotificationItem.SHORTFALL_EXPECTED.defaultFor(Member.Role.PARENT));
        assertNull(NotificationItem.MISSION_PROPOSED.defaultFor(Member.Role.CHILD)); // 자녀에게 없는 항목
    }
}
