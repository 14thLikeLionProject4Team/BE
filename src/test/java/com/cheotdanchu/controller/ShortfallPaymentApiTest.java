package com.cheotdanchu.controller;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

import static com.cheotdanchu.controller.SettlementFixture.openSettlement;
import static com.cheotdanchu.controller.SettlementFixture.overdueSettlement;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 시드 기준: 가족 A(루피 엄마 id 1, 루피 id 2)의 이번 정산(2026-10-25)은 아직 정산 전이고 청구 79,500원이다. 이번 달 쓸 수 있는 돈 73,000원,
 * 지난달까지 남은 돈 59,900원(계좌 전체 132,900원). 시드에는 결제일이 지난 부족 정산이 없어서, 테스트에서 이번 정산을 "결제일에 70,000원만
 * 이체돼 9,500원이 부족한 정산"(overdueSettlement)으로 바꿔서 쓴다. 테스트마다 롤백된다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ShortfallPaymentApiTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    EntityManager em;

    private ResultActions pay(int personaId, long settlementId, String source, Integer amount) throws Exception {
        String json = "{" + (source == null ? "" : "\"source\":\"" + source + "\"") + (source != null && amount != null ? "," : "")
                + (amount == null ? "" : "\"amount\":" + amount) + "}";
        return mockMvc.perform(post("/settlements/" + settlementId + "/shortfall-payments").header("X-Persona-Id", personaId)
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    /** 루피 계좌의 (전체 잔액, 지난달까지 남은 돈). 저장 대기 중인 변경을 먼저 DB 에 반영한다 */
    private long[] account() {
        em.flush();
        Map<String, Object> a = jdbc.queryForMap("SELECT balance, carryover_balance FROM accounts WHERE member_id = 2");
        return new long[]{((Number) a.get("balance")).longValue(), ((Number) a.get("carryover_balance")).longValue()};
    }

    private long shortfallPayments(long settlementId) {
        em.flush();
        return jdbc.queryForObject("SELECT COUNT(*) FROM transfers WHERE settlement_id = ? AND type = 'SHORTFALL_PAYMENT'", Long.class, settlementId);
    }

    // ---- 갚기 ----

    @Test
    void 남은_돈으로_일부를_갚으면_남은_돈이_줄고_쓸_수_있는_돈은_그대로고_일부_정산이_유지된다() throws Exception {
        long id = overdueSettlement(jdbc);
        long[] before = account();

        pay(2, id, "CARRYOVER", 4000)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("부족분을 갚았어요"))
                .andExpect(jsonPath("$.data.settlementId").value(id))
                .andExpect(jsonPath("$.data.source").value("CARRYOVER"))
                .andExpect(jsonPath("$.data.amount").value(4000))
                .andExpect(jsonPath("$.data.carryoverAmount").value(55900))
                .andExpect(jsonPath("$.data.remainingShortfall").value(5500))
                .andExpect(jsonPath("$.data.status").value("PARTIAL"))
                .andExpect(jsonPath("$.data.result").value(nullValue()));

        long[] after = account();
        assertEquals(before[0] - 4000, after[0], "계좌 전체 잔액이 갚은 만큼 줄어야 해요");
        assertEquals(before[1] - 4000, after[1], "지난달까지 남은 돈이 갚은 만큼 줄어야 해요");
        assertEquals(before[0] - before[1], after[0] - after[1], "이번 달 쓸 수 있는 돈은 그대로여야 해요");

        Map<String, Object> t = jdbc.queryForMap("SELECT amount, source, "
                + "(SELECT member_id FROM accounts WHERE account_id = from_account_id) AS from_member, "
                + "(SELECT member_id FROM accounts WHERE account_id = to_account_id) AS to_member "
                + "FROM transfers WHERE type = 'SHORTFALL_PAYMENT' AND settlement_id = ?", id);
        assertEquals(4000, ((Number) t.get("amount")).intValue());
        assertEquals("CARRYOVER", t.get("source"));
        assertEquals(2L, ((Number) t.get("from_member")).longValue()); // 루피 계좌 → 부모 계좌
        assertEquals(1L, ((Number) t.get("to_member")).longValue());
    }

    // 화면이 "얼마 갚아야 하나"를 갚기 전에 알 수 있도록 정산 상세가 남은 부족분을 알려준다. 갚기 응답의 남은 부족분과 같은 값이다
    @Test
    void 정산_상세의_남은_부족분은_갚을_때마다_줄고_갚기_응답과_같다() throws Exception {
        long id = overdueSettlement(jdbc);
        mockMvc.perform(get("/settlements/" + id).header("X-Persona-Id", 2))
                .andExpect(jsonPath("$.data.shortfallAmount").value(9500))
                .andExpect(jsonPath("$.data.remainingShortfall").value(9500));

        pay(2, id, "CARRYOVER", 4000).andExpect(jsonPath("$.data.remainingShortfall").value(5500));
        mockMvc.perform(get("/settlements/" + id).header("X-Persona-Id", 2))
                .andExpect(jsonPath("$.data.status").value("PARTIAL"))
                .andExpect(jsonPath("$.data.shortfallAmount").value(9500))      // 결제일에 부족했던 금액은 그대로
                .andExpect(jsonPath("$.data.remainingShortfall").value(5500));  // 남은 부족분은 갚은 만큼 줄어든다

        pay(2, id, "DIRECT", 5500).andExpect(jsonPath("$.data.remainingShortfall").value(0));
        mockMvc.perform(get("/settlements/" + id).header("X-Persona-Id", 2))
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.shortfallAmount").value(9500))
                .andExpect(jsonPath("$.data.remainingShortfall").value(0));
    }

    // 청구액이 아직 확정되지 않은(비어 있는) 정산은 카드 결제 합계(79,500원)를 청구액으로 보고 남은 부족분을 센다
    @Test
    void 청구액이_비어_있는_부족_정산도_카드_결제_합계로_남은_부족분을_센다() throws Exception {
        long id = overdueSettlement(jdbc);
        jdbc.update("UPDATE settlements SET billed_amount = NULL WHERE settlement_id = ?", id);

        pay(2, id, "CARRYOVER", 9501)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("amount: 남은 부족분(9,500원)을 넘을 수 없어요"));
        pay(2, id, "CARRYOVER", 9500)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.remainingShortfall").value(0))
                .andExpect(jsonPath("$.data.status").value("COMPLETED"));
    }

    @Test
    void 나눠서_전부_갚으면_스스로_해소로_끝나고_정산_상세에_그대로_나온다() throws Exception {
        long id = overdueSettlement(jdbc);

        pay(2, id, "CARRYOVER", 4000).andExpect(status().isCreated());
        pay(2, id, "DIRECT", 5500)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.remainingShortfall").value(0))
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.result").value("SELF_RESOLVED"));

        em.flush();
        assertEquals(true, jdbc.queryForObject("SELECT resolved_at IS NOT NULL FROM settlements WHERE settlement_id = ?", Boolean.class, id));
        // 정산 상세: 결제일에 부족했던 금액(9,500원)과 갚은 두 건이 시간순으로 나온다
        mockMvc.perform(get("/settlements/" + id).header("X-Persona-Id", 2))
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.result").value("SELF_RESOLVED"))
                .andExpect(jsonPath("$.data.paidAmount").value(70000))
                .andExpect(jsonPath("$.data.shortfallAmount").value(9500))
                .andExpect(jsonPath("$.data.transfers.length()").value(3))
                .andExpect(jsonPath("$.data.transfers[1].source").value("CARRYOVER"))
                .andExpect(jsonPath("$.data.transfers[2].source").value("DIRECT"));
        // 정산 목록에는 끝난 정산의 결과로 나온다
        mockMvc.perform(get("/settlements").header("X-Persona-Id", 2))
                .andExpect(jsonPath("$.data.items[?(@.settlementId == " + id + ")].result").value("SELF_RESOLVED"));
    }

    @Test
    void 직접_송금은_갚은_기록만_남고_계좌_잔액은_그대로다() throws Exception {
        long id = overdueSettlement(jdbc);
        long[] before = account();

        pay(2, id, "DIRECT", 9500)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.source").value("DIRECT"))
                .andExpect(jsonPath("$.data.carryoverAmount").value(59900))
                .andExpect(jsonPath("$.data.remainingShortfall").value(0))
                .andExpect(jsonPath("$.data.status").value("COMPLETED"));

        long[] after = account();
        assertEquals(before[0], after[0]);
        assertEquals(before[1], after[1]);
    }

    @Test
    void 남은_돈과_직접_송금을_섞어서_갚을_수_있고_남은_돈에서는_그만큼만_나간다() throws Exception {
        long id = overdueSettlement(jdbc);

        pay(2, id, "CARRYOVER", 2000).andExpect(status().isCreated());
        pay(2, id, "DIRECT", 7500)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.remainingShortfall").value(0))
                .andExpect(jsonPath("$.data.carryoverAmount").value(57900));
    }

    // ---- 갚을 수 없는 경우: 돈과 이체가 하나도 바뀌지 않는다 ----

    @Test
    void 남은_부족분을_넘게_갚으려_하면_400이고_아무것도_바뀌지_않는다() throws Exception {
        long id = overdueSettlement(jdbc);
        long[] before = account();

        pay(2, id, "CARRYOVER", 9501)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("amount: 남은 부족분(9,500원)을 넘을 수 없어요"));

        assertEquals(before[0], account()[0]);
        assertEquals(before[1], account()[1]);
        assertEquals(0, shortfallPayments(id));
        assertEquals("SHORTFALL", jdbc.queryForObject("SELECT status FROM settlements WHERE settlement_id = ?", String.class, id));
    }

    @Test
    void 지난달까지_남은_돈보다_많이_갚으려_하면_400이고_직접_송금은_된다() throws Exception {
        long id = overdueSettlement(jdbc);
        // 남은 돈을 5,000원으로 줄인다 (계좌 전체 잔액은 이번 달 쓸 수 있는 돈 73,000원 + 5,000원)
        jdbc.update("UPDATE accounts SET carryover_balance = 5000, balance = 78000 WHERE member_id = 2");

        pay(2, id, "CARRYOVER", 9500)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("지난달까지 남은 돈(5,000원)보다 많이 갚을 수 없어요"));
        assertEquals(0, shortfallPayments(id));

        pay(2, id, "DIRECT", 9500).andExpect(status().isCreated());
    }

    @Test
    void 이미_다_갚은_정산과_끝난_정산은_갚을_부족분이_없어_409다() throws Exception {
        long id = overdueSettlement(jdbc);
        pay(2, id, "CARRYOVER", 9500).andExpect(status().isCreated());

        pay(2, id, "CARRYOVER", 100)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("보낼 부족분이 없어요"));
        // 시드의 끝난 정산(완납)도 갚을 부족분이 없다
        pay(2, jdbc.queryForObject("SELECT settlement_id FROM settlements WHERE family_id = 1 AND period_end = '2026-09-25'", Long.class),
                "DIRECT", 100).andExpect(status().isConflict());
    }

    // 결제일 전에는 아직 부족분이 확정되지 않았다: 부족 예상이 있어도 갚을 수 없다 (결제일에 쓸 수 있는 돈 전액이 먼저 이체된다)
    @Test
    void 결제일_전_정산은_부족_예상이_있어도_갚을_수_없고_돈이_그대로다() throws Exception {
        long id = openSettlement(jdbc); // 정산 전(SCHEDULED), 부족 예상 6,500원
        long[] before = account();

        pay(2, id, "CARRYOVER", 6500)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("결제일이 지난 뒤에 갚을 수 있어요"));
        jdbc.update("UPDATE settlements SET status = 'REQUIRED' WHERE settlement_id = ?", id);
        pay(2, id, "DIRECT", 6500)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("결제일이 지난 뒤에 갚을 수 있어요"));

        assertEquals(before[0], account()[0]);
        assertEquals(before[1], account()[1]);
        assertEquals(0, shortfallPayments(id));
    }

    // ---- 입력·권한 ----

    @Test
    void 금액이_1원_미만이거나_빠졌거나_방법이_잘못되면_400이다() throws Exception {
        long id = overdueSettlement(jdbc);

        pay(2, id, "CARRYOVER", 0).andExpect(status().isBadRequest());
        pay(2, id, "CARRYOVER", -1).andExpect(status().isBadRequest());
        pay(2, id, "CARRYOVER", null).andExpect(status().isBadRequest());
        pay(2, id, null, 1000).andExpect(status().isBadRequest());
        pay(2, id, "CASH", 1000).andExpect(status().isBadRequest());
        assertEquals(0, shortfallPayments(id));
    }

    @Test
    void 부모가_갚으면_403_남의_가족_정산이거나_없는_정산이면_404다() throws Exception {
        long id = overdueSettlement(jdbc);

        pay(1, id, "CARRYOVER", 1000)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("자녀만 조회할 수 있어요"));
        // 가족 B(뽀로로 id 4)가 가족 A 의 정산을 갚으려 하면 존재 여부를 알려주지 않는다
        pay(4, id, "DIRECT", 1000).andExpect(status().isNotFound());
        pay(2, 999999, "DIRECT", 1000).andExpect(status().isNotFound());
        assertEquals(0, shortfallPayments(id));
    }
}
