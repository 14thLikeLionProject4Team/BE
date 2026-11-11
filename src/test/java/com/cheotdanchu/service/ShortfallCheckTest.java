package com.cheotdanchu.service;

import com.cheotdanchu.dto.ShortfallCheckResponse;
import com.cheotdanchu.entity.Settlement;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 부족 예상 점검 카드의 표시 조건과 안내 문구 (DB 없이 계산만 확인) */
class ShortfallCheckTest {

    private static final LocalDate DUE = LocalDate.of(2026, 10, 25);

    private ShortfallCheckResponse check(long dDay, long billed, long available, long carryover) {
        return SettlementService.check(DUE, Settlement.Status.SCHEDULED, dDay, billed, available, carryover);
    }

    private ShortfallCheckResponse check(Settlement.Status status) {
        return SettlementService.check(DUE, status, 3, 79500, 73000, 59900);
    }

    @Test
    void 결제일_도래_상태에서도_보여준다() {
        assertTrue(check(Settlement.Status.REQUIRED).visible());
    }

    @Test
    void 정산이_시작된_뒤의_상태에서는_부족해도_보여주지_않는다() {
        assertFalse(check(Settlement.Status.SHORTFALL).visible());
        assertFalse(check(Settlement.Status.PARTIAL).visible());
        assertFalse(check(Settlement.Status.COMPLETED).visible());
    }

    @Test
    void D6에_부족하면_점검_카드를_보여준다() {
        ShortfallCheckResponse r = check(6, 79500, 73000, 59900);

        assertTrue(r.visible());
        assertEquals(6500, r.shortfallAmount());
        assertEquals("지금 속도면 결제일에 6,500원이 모자랄 것 같아요", r.message());
    }

    @Test
    void 결제일_당일까지는_보여준다() {
        assertTrue(check(0, 79500, 73000, 0).visible());
    }

    @Test
    void D7이면_부족해도_아직_보여주지_않는다() {
        assertFalse(check(7, 79500, 73000, 59900).visible());
    }

    @Test
    void 결제일이_지났으면_보여주지_않는다() {
        assertFalse(check(-1, 79500, 73000, 59900).visible());
    }

    // 명세의 표시 안 함 예시: D-13, 청구 59,600원, 쓸 수 있는 돈 80,000원, 남은 돈 12,000원
    @Test
    void 부족하지_않으면_표시하지_않고_부족액_메시지_안내는_비운다() {
        ShortfallCheckResponse r = check(13, 59600, 80000, 12000);

        assertFalse(r.visible());
        assertEquals(0, r.shortfallAmount());
        assertNull(r.message());
        assertEquals(List.of(), r.guides());
        assertEquals(59600, r.billedAmount());
        assertEquals(80000, r.availableAmount());
        assertEquals(12000, r.carryoverAmount());
    }

    @Test
    void 쓸_수_있는_돈과_청구액이_같으면_부족이_아니다() {
        assertFalse(check(3, 70000, 70000, 0).visible());
    }

    // 명세 예시: 남은 돈 12,000원으로 갚을 수 있으면 그 사실을 안내한다
    @Test
    void 남은_돈으로_메울_수_있으면_갚을_수_있다고_안내한다() {
        ShortfallCheckResponse r = check(6, 79500, 73000, 12000);

        assertEquals(List.of(
                "지난달까지 남은 돈 12,000원으로 갚을 수 있어요",
                "다음 용돈 전까지 직접 보내도 돼요",
                "다른 사람에게 빌리지 마세요"), r.guides());
    }

    @Test
    void 남은_돈이_부족분보다_적으면_일부만_갚을_수_있다고_안내한다() {
        ShortfallCheckResponse r = check(6, 79500, 73000, 2000);

        assertEquals("지난달까지 남은 돈 2,000원으로 일부 갚을 수 있어요", r.guides().get(0));
        assertEquals(3, r.guides().size());
    }

    @Test
    void 남은_돈이_없으면_남은_돈_안내는_빼고_나머지만_준다() {
        ShortfallCheckResponse r = check(6, 79500, 73000, 0);

        assertEquals(List.of("다음 용돈 전까지 직접 보내도 돼요", "다른 사람에게 빌리지 마세요"), r.guides());
    }
}
