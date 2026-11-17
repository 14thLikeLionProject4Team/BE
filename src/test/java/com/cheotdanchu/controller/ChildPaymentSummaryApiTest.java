package com.cheotdanchu.controller;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 시드 기준: 가족 A(루피 엄마 id 1, 루피 id 2)의 이번 정산(2026-09-26~10-25, 결제일 10-25)은 14건 79,500원.
 * 가족 B(뽀로로 엄마 id 3, 뽀로로 id 4)는 진행 중인 정산이 없다. 테스트마다 롤백된다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ChildPaymentSummaryApiTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbc;

    private ResultActions summary(int personaId) throws Exception {
        return mockMvc.perform(get("/child/payments/summary").header("X-Persona-Id", personaId));
    }

    @Test
    void 이번_정산의_청구_예정액과_결제_건수와_결제일을_본다() throws Exception {
        summary(2)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("조회 성공"))
                .andExpect(jsonPath("$.data.periodStart").value("2026-09-26"))
                .andExpect(jsonPath("$.data.periodEnd").value("2026-10-25"))
                .andExpect(jsonPath("$.data.billedAmount").value(79500))
                .andExpect(jsonPath("$.data.paymentCount").value(14))
                .andExpect(jsonPath("$.data.dueDate").value("2026-10-25"));
    }

    @Test
    void 현재_정산_조회와_같은_정산_같은_숫자다() throws Exception {
        String current = mockMvc.perform(get("/settlements/current").header("X-Persona-Id", 2)).andReturn().getResponse().getContentAsString();
        long settlementId = ((Number) JsonPath.read(current, "$.data.settlementId")).longValue();
        long billed = ((Number) JsonPath.read(current, "$.data.billedAmount")).longValue();
        long count = ((Number) JsonPath.read(current, "$.data.paymentCount")).longValue();

        summary(2)
                .andExpect(jsonPath("$.data.settlementId").value(settlementId))
                .andExpect(jsonPath("$.data.billedAmount").value(billed))
                .andExpect(jsonPath("$.data.paymentCount").value(count));
    }

    @Test
    void 취소는_금액에_합산하고_취소된_결제는_건수에서_뺀다() throws Exception {
        Long settlementId = jdbc.queryForObject(
                "SELECT settlement_id FROM settlements WHERE family_id = 1 AND '2026-10-19' BETWEEN period_start AND period_end", Long.class);
        jdbc.update("INSERT INTO card_payments (member_id, settlement_id, merchant_name, category, amount, is_canceled, paid_at) "
                + "VALUES (2, ?, '취소 가맹점', '식사', -2000, TRUE, '2026-10-18 12:00:00')", settlementId);

        summary(2)
                .andExpect(jsonPath("$.data.billedAmount").value(77500))
                .andExpect(jsonPath("$.data.paymentCount").value(13)); // 원래 결제 1건과 취소 1건이 함께 빠진다
    }

    @Test
    void 진행_중인_정산이_없으면_data가_null인_200() throws Exception {
        summary(4)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").value(nullValue()));
    }

    @Test
    void 부모가_조회하면_403() throws Exception {
        summary(1)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("자녀만 조회할 수 있어요"));
    }

    @Test
    void 취소가_있어도_자녀_홈의_현재_정산_조회와_같은_건수다() throws Exception {
        Long settlementId = jdbc.queryForObject(
                "SELECT settlement_id FROM settlements WHERE family_id = 1 AND '2026-10-19' BETWEEN period_start AND period_end", Long.class);
        jdbc.update("INSERT INTO card_payments (member_id, settlement_id, merchant_name, category, amount, is_canceled, paid_at) "
                + "VALUES (2, ?, '취소 가맹점', '식사', -2000, TRUE, '2026-10-18 12:00:00')", settlementId);

        mockMvc.perform(get("/settlements/current").header("X-Persona-Id", 2))
                .andExpect(jsonPath("$.data.paymentCount").value(13));
        summary(2).andExpect(jsonPath("$.data.paymentCount").value(13));
    }

    @Test
    void 모든_결제가_취소돼도_건수는_0_아래로_내려가지_않는다() throws Exception {
        Long settlementId = jdbc.queryForObject(
                "SELECT settlement_id FROM settlements WHERE family_id = 1 AND '2026-10-19' BETWEEN period_start AND period_end", Long.class);
        for (int i = 0; i < 16; i++) { // 결제 14건보다 취소가 더 많은 어긋난 데이터
            jdbc.update("INSERT INTO card_payments (member_id, settlement_id, merchant_name, category, amount, is_canceled, paid_at) "
                    + "VALUES (2, ?, '취소 가맹점', '식사', -100, TRUE, '2026-10-18 12:00:00')", settlementId);
        }

        summary(2).andExpect(jsonPath("$.data.paymentCount").value(0));
    }
}
