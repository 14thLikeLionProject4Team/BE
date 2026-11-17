package com.cheotdanchu.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 시드 기준(데모 기준일 10/19, 결제일 10/25 = D-6): 루피 이번 기간 결제 79,500원, 가장 많이 쓴 항목 식사 29,000원(3번, 평균 9,666원),
 * 이번 달 쓸 수 있는 돈 73,000원 → 부족 예상 6,500원. 테스트마다 롤백
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SavingTipApiTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void 부족_예상이면_몇_번_줄이면_채워지는지_알려준다() throws Exception {
        mockMvc.perform(get("/missions/saving-tip").header("X-Persona-Id", 2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("절약 팁 조회 성공"))
                .andExpect(jsonPath("$.data.text").value("식사 한 번만 줄이면 부족분이 채워져요."))
                .andExpect(jsonPath("$.data.link.category").value("식사"))
                .andExpect(jsonPath("$.data.link.sort").value("amount"));
    }

    @Test
    void 부족분이_크면_여러_번() throws Exception {
        // 쓸 수 있는 돈을 20,000원 줄이면 부족 26,500원 → 9,666원씩 세 번
        jdbc.update("UPDATE accounts SET balance = balance - 20000 WHERE member_id = 2");

        mockMvc.perform(get("/missions/saving-tip").header("X-Persona-Id", 2))
                .andExpect(jsonPath("$.data.text").value("식사 세 번만 줄이면 부족분이 채워져요."));
    }

    @Test
    void 부족하지_않으면_한_번_줄일_때_아끼는_돈을_알려준다() throws Exception {
        jdbc.update("UPDATE accounts SET balance = balance + 10000 WHERE member_id = 2");

        mockMvc.perform(get("/missions/saving-tip").header("X-Persona-Id", 2))
                .andExpect(jsonPath("$.data.text").value("식사에 가장 많이 썼어요. 한 번만 줄여도 9,666원을 아낄 수 있어요."));
    }

    @Test
    void 취소한_결제는_횟수에서_빼고_평균을_낸다() throws Exception {
        // 식사 10,000원 결제 후 취소(마이너스 행) → 식사 29,000원 3번 그대로, 평균 9,666원
        Long settlementId = jdbc.queryForObject(
                "SELECT settlement_id FROM settlements WHERE family_id = 1 AND period_start <= '2026-10-19' AND period_end >= '2026-10-19'", Long.class);
        jdbc.update("INSERT INTO card_payments (member_id, settlement_id, merchant_name, category, amount, is_canceled, paid_at) VALUES (2, ?, '분식집', '식사', 10000, 0, '2026-10-10 12:00:00')", settlementId);
        jdbc.update("INSERT INTO card_payments (member_id, settlement_id, merchant_name, category, amount, is_canceled, paid_at) VALUES (2, ?, '분식집', '식사', -10000, 1, '2026-10-10 13:00:00')", settlementId);
        jdbc.update("UPDATE accounts SET balance = balance + 10000 WHERE member_id = 2");

        mockMvc.perform(get("/missions/saving-tip").header("X-Persona-Id", 2))
                .andExpect(jsonPath("$.data.text").value("식사에 가장 많이 썼어요. 한 번만 줄여도 9,666원을 아낄 수 있어요."));
    }

    @Test
    void 이번_기간_결제가_없으면_data_null() throws Exception {
        jdbc.update("DELETE FROM card_payments WHERE member_id = 2 AND paid_at >= '2026-09-26'");

        mockMvc.perform(get("/missions/saving-tip").header("X-Persona-Id", 2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(nullValue()));
    }

    @Test
    void 부모가_호출하면_403() throws Exception {
        mockMvc.perform(get("/missions/saving-tip").header("X-Persona-Id", 1)).andExpect(status().isForbidden());
    }
}
