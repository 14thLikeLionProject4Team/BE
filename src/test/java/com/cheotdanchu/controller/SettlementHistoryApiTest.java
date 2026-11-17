package com.cheotdanchu.controller;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 시드 기준
 * - 가족 A(루피 엄마 id 1, 루피 id 2): 끝난 정산 11건(2025-11 ~ 2026-09)과 정산 전인 이번 달 1건. 2026-03 정산은 5,200원이 부족해서 다음 용돈에서 자동 정산됐다.
 * - 가족 B(뽀로로 엄마 id 3, 뽀로로 id 4): 끝난 정산 27건. 부족분을 직접 보낸 달(2024-10)과 남은 돈으로 갚은 달(2025-01·2025-06)이 있다.
 * 테스트마다 롤백된다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SettlementHistoryApiTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbc;

    private ResultActions list(int personaId, Integer size, String cursor) throws Exception {
        MockHttpServletRequestBuilder request = get("/settlements").header("X-Persona-Id", personaId);
        if (size != null) request.param("size", size.toString());
        if (cursor != null) request.param("cursor", cursor);
        return mockMvc.perform(request);
    }

    private ResultActions detail(int personaId, long settlementId) throws Exception {
        return mockMvc.perform(get("/settlements/" + settlementId).header("X-Persona-Id", personaId));
    }

    /** 가족의 정산 ID 를 종료일로 찾는다 (시드 적재 순서에 기대지 않는다) */
    private long settlementOf(int familyId, String periodEnd) {
        return jdbc.queryForObject("SELECT settlement_id FROM settlements WHERE family_id = ? AND period_end = ?", Long.class, familyId, periodEnd);
    }

    private List<Long> expectedIds(int familyId) {
        return jdbc.queryForList("SELECT settlement_id FROM settlements WHERE family_id = ? AND status <> 'SCHEDULED' ORDER BY period_end DESC",
                Long.class, familyId);
    }

    /** 첫 페이지부터 nextCursor 를 따라 끝까지 읽은 정산 ID 순서 */
    private List<Long> readAllPages(int personaId, Integer size) throws Exception {
        List<Long> ids = new ArrayList<>();
        String cursor = null;
        for (int guard = 0; guard < 50; guard++) {
            String body = list(personaId, size, cursor).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            List<Number> page = JsonPath.read(body, "$.data.items[*].settlementId");
            page.forEach(n -> ids.add(n.longValue()));
            cursor = JsonPath.read(body, "$.data.nextCursor");
            if (cursor == null) return ids;
        }
        throw new AssertionError("페이지가 끝나지 않아요");
    }

    // ---- 목록 ----

    @Test
    void 끝난_정산을_최신순으로_보고_정산_전인_이번_달은_넣지_않는다() throws Exception {
        list(2, null, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("조회 성공"))
                .andExpect(jsonPath("$.data.totalCount").value(11))
                .andExpect(jsonPath("$.data.items.length()").value(11))
                .andExpect(jsonPath("$.data.nextCursor").value(nullValue()))
                .andExpect(jsonPath("$.data.items[0].periodStart").value("2026-08-26"))
                .andExpect(jsonPath("$.data.items[0].periodEnd").value("2026-09-25"))
                .andExpect(jsonPath("$.data.items[0].status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.items[0].result").value("FULL_PAID"))
                .andExpect(jsonPath("$.data.items[0].billedAmount").value(61500))
                .andExpect(jsonPath("$.data.items[0].isExcluded").value(false));
        assertEquals(expectedIds(1), readAllPages(2, null));
    }

    @Test
    void 자동_정산된_달은_결과가_AUTO_DEDUCTED로_나온다() throws Exception {
        long autoDeducted = settlementOf(1, "2026-03-25");

        String body = list(2, null, null).andReturn().getResponse().getContentAsString();
        List<Object> hit = JsonPath.read(body, "$.data.items[?(@.settlementId == " + autoDeducted + ")].result");
        assertEquals(List.of("AUTO_DEDUCTED"), hit);
    }

    @Test
    void 기록_지표에서_빠진_정산은_isExcluded가_true다() throws Exception {
        long id = settlementOf(1, "2026-08-25");
        jdbc.update("UPDATE settlements SET is_excluded = TRUE WHERE settlement_id = ?", id);

        String body = list(2, null, null).andReturn().getResponse().getContentAsString();
        List<Boolean> excluded = JsonPath.read(body, "$.data.items[?(@.settlementId == " + id + ")].isExcluded");
        assertEquals(List.of(true), excluded);
    }

    @Test
    void 정산이_스물일곱_개인_가족은_기본_스무_개씩_넘기고_빠지거나_겹치지_않는다() throws Exception {
        list(4, null, null)
                .andExpect(jsonPath("$.data.totalCount").value(27))
                .andExpect(jsonPath("$.data.items.length()").value(20));

        for (Integer size : new Integer[]{null, 1, 5, 10, 27}) {
            assertEquals(expectedIds(2), readAllPages(4, size), "size=" + size);
        }
    }

    @Test
    void 한_페이지가_가득_차도_다음이_없으면_nextCursor는_null이다() throws Exception {
        list(2, 11, null)
                .andExpect(jsonPath("$.data.items.length()").value(11))
                .andExpect(jsonPath("$.data.nextCursor").value(nullValue()));
        list(2, 10, null)
                .andExpect(jsonPath("$.data.items.length()").value(10))
                .andExpect(jsonPath("$.data.totalCount").value(11));
    }

    @Test
    void 잘못된_크기와_깨진_커서는_400이다() throws Exception {
        list(2, 0, null).andExpect(status().isBadRequest());
        list(2, 101, null).andExpect(status().isBadRequest());
        list(2, null, "깨진-커서").andExpect(status().isBadRequest());
        // 형식(base64)은 맞지만 날짜가 아닌 값
        list(2, null, cursorOf("abc")).andExpect(status().isBadRequest());
    }

    // 연도가 DB 가 받는 범위(1~9999)를 벗어난 날짜를 그대로 쿼리에 넣으면 DB 오류(500)가 된다
    @Test
    void 연도가_범위를_벗어난_커서는_500이_아니라_400이다() throws Exception {
        list(2, null, cursorOf("+99999-01-01")).andExpect(status().isBadRequest());
        list(2, null, cursorOf("-0001-01-01")).andExpect(status().isBadRequest());
        list(2, null, cursorOf("0000-01-01")).andExpect(status().isBadRequest());
        list(2, null, cursorOf("9999-12-31")).andExpect(status().isOk()); // 범위의 끝은 허용
    }

    private static String cursorOf(String raw) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    // ---- 상세 ----

    @Test
    void 완납한_달은_결제일_정산_이체_하나와_정산이_끝난_시각을_본다() throws Exception {
        detail(2, settlementOf(1, "2026-09-25"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("조회 성공"))
                .andExpect(jsonPath("$.data.periodStart").value("2026-08-26"))
                .andExpect(jsonPath("$.data.periodEnd").value("2026-09-25"))
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.result").value("FULL_PAID"))
                .andExpect(jsonPath("$.data.billedAmount").value(61500))
                .andExpect(jsonPath("$.data.paidAmount").value(61500))
                .andExpect(jsonPath("$.data.shortfallAmount").value(0))
                .andExpect(jsonPath("$.data.isExcluded").value(false))
                .andExpect(jsonPath("$.data.resolvedAt").value("2026-09-25T18:00:00"))
                .andExpect(jsonPath("$.data.transfers.length()").value(1))
                .andExpect(jsonPath("$.data.transfers[0].type").value("SETTLEMENT"))
                .andExpect(jsonPath("$.data.transfers[0].source").value(nullValue()))
                .andExpect(jsonPath("$.data.transfers[0].amount").value(61500))
                .andExpect(jsonPath("$.data.transfers[0].transferredAt").value("2026-09-25T18:00:00"));
    }

    @Test
    void 자동_정산된_달은_부족액과_자동_정산_이체가_함께_나온다() throws Exception {
        detail(2, settlementOf(1, "2026-03-25"))
                .andExpect(jsonPath("$.data.result").value("AUTO_DEDUCTED"))
                .andExpect(jsonPath("$.data.billedAmount").value(75200))
                .andExpect(jsonPath("$.data.paidAmount").value(70000))
                .andExpect(jsonPath("$.data.shortfallAmount").value(5200))
                .andExpect(jsonPath("$.data.resolvedAt").value("2026-04-01T09:00:00"))
                .andExpect(jsonPath("$.data.transfers.length()").value(2))
                .andExpect(jsonPath("$.data.transfers[0].type").value("SETTLEMENT"))
                .andExpect(jsonPath("$.data.transfers[0].amount").value(70000))
                .andExpect(jsonPath("$.data.transfers[1].type").value("AUTO_DEDUCT"))
                .andExpect(jsonPath("$.data.transfers[1].amount").value(5200))
                .andExpect(jsonPath("$.data.transfers[1].transferredAt").value("2026-04-01T09:00:00"));
    }

    @Test
    void 스스로_해소한_달은_부족분을_어떻게_갚았는지_source가_나온다() throws Exception {
        // 직접 송금한 달
        detail(4, settlementOf(2, "2024-10-25"))
                .andExpect(jsonPath("$.data.result").value("SELF_RESOLVED"))
                .andExpect(jsonPath("$.data.shortfallAmount").value(4600))
                .andExpect(jsonPath("$.data.transfers[1].type").value("SHORTFALL_PAYMENT"))
                .andExpect(jsonPath("$.data.transfers[1].source").value("DIRECT"))
                .andExpect(jsonPath("$.data.transfers[1].amount").value(4600))
                .andExpect(jsonPath("$.data.resolvedAt").value("2024-10-28T20:00:00"));
        // 지난달까지 남은 돈으로 갚은 달
        detail(4, settlementOf(2, "2025-01-25"))
                .andExpect(jsonPath("$.data.result").value("SELF_RESOLVED"))
                .andExpect(jsonPath("$.data.transfers[1].type").value("SHORTFALL_PAYMENT"))
                .andExpect(jsonPath("$.data.transfers[1].source").value("CARRYOVER"))
                .andExpect(jsonPath("$.data.transfers[1].amount").value(7200));
    }

    @Test
    void 정산_전인_이번_달은_청구액을_카드_결제로_보여주고_이체와_결과가_없다() throws Exception {
        detail(2, settlementOf(1, "2026-10-25"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SCHEDULED"))
                .andExpect(jsonPath("$.data.result").value(nullValue()))
                .andExpect(jsonPath("$.data.billedAmount").value(79500))
                .andExpect(jsonPath("$.data.paidAmount").value(0))
                .andExpect(jsonPath("$.data.shortfallAmount").value(0))
                .andExpect(jsonPath("$.data.resolvedAt").value(nullValue()))
                .andExpect(jsonPath("$.data.transfers").isEmpty());
    }

    @Test
    void 이체는_시간순이고_용돈과_미션_보상은_정산_이체에_섞이지_않는다() throws Exception {
        // 이번 달에는 용돈·미션 보상 이체가 있지만 정산에 연결된 것이 아니라서 이체 내역에 나오지 않는다
        assertEquals(true, jdbc.queryForObject(
                "SELECT COUNT(*) > 0 FROM transfers WHERE settlement_id IS NULL AND type IN ('ALLOWANCE', 'MISSION_REWARD')", Boolean.class));

        String body = detail(2, settlementOf(1, "2026-03-25")).andReturn().getResponse().getContentAsString();
        List<String> times = JsonPath.read(body, "$.data.transfers[*].transferredAt");
        assertEquals(times.stream().sorted().toList(), times);
        List<String> types = JsonPath.read(body, "$.data.transfers[*].type");
        assertEquals(List.of("SETTLEMENT", "AUTO_DEDUCT"), types);
    }

    /** 가족 A 의 이번 달 정산 ID (시드에서는 정산 전) */
    private long openSettlement() {
        return settlementOf(1, "2026-10-25");
    }

    /** 결제일 정산 이체를 넣는다 (루피 계좌 → 부모 계좌) */
    private void insertSettlementTransfer(long settlementId, int amount) {
        jdbc.update("INSERT INTO transfers (from_account_id, to_account_id, type, amount, settlement_id, transferred_at) "
                + "SELECT c.account_id, p.account_id, 'SETTLEMENT', ?, ?, '2026-10-25 18:00:00' "
                + "FROM accounts c, accounts p WHERE c.member_id = 2 AND p.member_id = 1", amount, settlementId);
    }

    /** 이번 달 정산을 부족이 남은 상태(SHORTFALL)로 만들고 결제일에 paid 원을 이체한 것으로 한다. billed 가 null 이면 청구액이 비어 있는 정산 */
    private long shortfallSettlement(Integer billed, int paid) {
        long id = openSettlement();
        jdbc.update("UPDATE settlements SET status = 'SHORTFALL', billed_amount = ? WHERE settlement_id = ?", billed, id);
        insertSettlementTransfer(id, paid);
        return id;
    }

    @Test
    void 결제일이_되어_정산이_필요한_상태는_부족액을_아직_말하지_않는다() throws Exception {
        jdbc.update("UPDATE settlements SET status = 'REQUIRED' WHERE settlement_id = ?", openSettlement());

        detail(2, openSettlement())
                .andExpect(jsonPath("$.data.status").value("REQUIRED"))
                .andExpect(jsonPath("$.data.billedAmount").value(79500))
                .andExpect(jsonPath("$.data.shortfallAmount").value(0));
    }

    @Test
    void 부족이_남은_상태는_청구액에서_결제일_이체를_뺀_부족액을_보여준다() throws Exception {
        long id = shortfallSettlement(79500, 70000);

        detail(2, id)
                .andExpect(jsonPath("$.data.status").value("SHORTFALL"))
                .andExpect(jsonPath("$.data.result").value(nullValue()))
                .andExpect(jsonPath("$.data.billedAmount").value(79500))
                .andExpect(jsonPath("$.data.paidAmount").value(70000))
                .andExpect(jsonPath("$.data.shortfallAmount").value(9500))
                .andExpect(jsonPath("$.data.resolvedAt").value(nullValue()));
        jdbc.update("UPDATE settlements SET status = 'PARTIAL' WHERE settlement_id = ?", id);
        detail(2, id).andExpect(jsonPath("$.data.shortfallAmount").value(9500));
    }

    @Test
    void 청구액이_비어_있는_정산은_카드_결제_합계로_부족액을_센다() throws Exception {
        long id = shortfallSettlement(null, 70000);

        detail(2, id)
                .andExpect(jsonPath("$.data.billedAmount").value(79500))
                .andExpect(jsonPath("$.data.shortfallAmount").value(9500));
    }

    // 부족분 갚기(A-5)가 settlements.paid_amount 를 늘려도, 결제일에 부족했던 금액과 이체 내역은 그대로여야 한다
    @Test
    void paid_amount가_바뀌어도_결제일의_부족액은_그대로다() throws Exception {
        long autoDeducted = settlementOf(1, "2026-03-25");
        jdbc.update("UPDATE settlements SET paid_amount = 75200 WHERE settlement_id = ?", autoDeducted);

        detail(2, autoDeducted)
                .andExpect(jsonPath("$.data.paidAmount").value(70000))
                .andExpect(jsonPath("$.data.shortfallAmount").value(5200));
    }

    @Test
    void 남의_가족_정산이거나_없는_정산이면_404다() throws Exception {
        detail(2, settlementOf(2, "2026-09-25")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("정산을 찾을 수 없습니다"));
        detail(4, settlementOf(1, "2026-09-25")).andExpect(status().isNotFound());
        detail(2, 999999).andExpect(status().isNotFound());
    }

    @Test
    void 부모가_조회하면_403이다() throws Exception {
        list(1, null, null)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("자녀만 조회할 수 있어요"));
        detail(1, settlementOf(1, "2026-09-25")).andExpect(status().isForbidden());
        detail(3, settlementOf(2, "2026-09-25")).andExpect(status().isForbidden());
    }
}
