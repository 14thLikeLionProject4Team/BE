package com.cheotdanchu.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 서버가 켜질 때 schema.sql·data.sql·SeedLoader 가 실행된 개발 DB를 그대로 쓴다 (MySQL 필요). 테스트마다 롤백돼서 넣은 데이터가 남지 않는다 */
@SpringBootTest
@Transactional
@AutoConfigureMockMvc
class RecordsSummaryApiTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbc;

    // 루피(가족 A 자녀, id 2): 끝난 정산 11개월 (진행 중인 10월 정산은 들어가지 않는다)
    @Test
    void 가족A_자녀는_명세_예시와_같은_지표를_받는다() throws Exception {
        mockMvc.perform(get("/records/summary").header("X-Persona-Id", 2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("조회 성공"))
                .andExpect(jsonPath("$.data.fullPaidMonths").value(10))
                .andExpect(jsonPath("$.data.totalMonths").value(11))
                .andExpect(jsonPath("$.data.maxStreak").value(6))
                .andExpect(jsonPath("$.data.avgMarginRate").value(7.5))
                .andExpect(jsonPath("$.data.selfResolvedCount").value(0))
                .andExpect(jsonPath("$.data.shortfallCount").value(1));
    }

    // 뽀로로(가족 B 자녀, id 4): 27개월 중 24개월 완납, 부족 3번은 모두 스스로 해소했다 (시드의 stats 와 같은 값)
    @Test
    void 가족B_자녀는_자기_가족의_정산으로_계산된다() throws Exception {
        mockMvc.perform(get("/records/summary").header("X-Persona-Id", 4))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fullPaidMonths").value(24))
                .andExpect(jsonPath("$.data.totalMonths").value(27))
                .andExpect(jsonPath("$.data.maxStreak").value(15))
                .andExpect(jsonPath("$.data.selfResolvedCount").value(3))
                .andExpect(jsonPath("$.data.shortfallCount").value(3));
    }

    // 제외된 정산(부모 지원·현금)과 기록이 비어 있는 정산이 끼어 있어도 루피의 지표는 그대로여야 한다
    @Test
    void 제외된_정산과_기록이_비어_있는_정산은_지표에서_빠진다() throws Exception {
        String insert = "INSERT INTO settlements (family_id, period_start, period_end, due_at, billed_amount, balance_at_due, "
                + "paid_amount, status, result, is_excluded) VALUES (1, ?, ?, ?, ?, ?, ?, 'COMPLETED', ?, ?)";
        jdbc.update(insert, "2030-01-26", "2030-02-25", "2030-02-25 18:00:00", 50000, 70000, 50000, "FULL_PAID", true);
        jdbc.update(insert, "2030-02-26", "2030-03-25", "2030-03-25 18:00:00", null, null, 0, null, false);

        mockMvc.perform(get("/records/summary").header("X-Persona-Id", 2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fullPaidMonths").value(10))
                .andExpect(jsonPath("$.data.totalMonths").value(11))
                .andExpect(jsonPath("$.data.maxStreak").value(6))
                .andExpect(jsonPath("$.data.shortfallCount").value(1));
    }

    @Test
    void 부모가_호출하면_403() throws Exception {
        mockMvc.perform(get("/records/summary").header("X-Persona-Id", 1))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("자녀만 조회할 수 있어요"));
    }

    @Test
    void 없는_페르소나는_404() throws Exception {
        mockMvc.perform(get("/records/summary").header("X-Persona-Id", 99))
                .andExpect(status().isNotFound());
    }

    @Test
    void 헤더가_없으면_400() throws Exception {
        mockMvc.perform(get("/records/summary"))
                .andExpect(status().isBadRequest());
    }
}
