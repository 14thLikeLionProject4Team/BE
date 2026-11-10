package com.cheotdanchu.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 서버가 켜질 때 schema.sql·data.sql·SeedLoader 가 실행된 DB를 그대로 쓴다 (MySQL 필요) */
@SpringBootTest
@AutoConfigureMockMvc
class SettlementCurrentTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbc;

    // 루피(가족 A 자녀, id 2): 명세·시드 기준 숫자
    @Test
    void 가족A_자녀는_현재_정산을_조회한다() throws Exception {
        mockMvc.perform(get("/settlements/current").header("X-Persona-Id", 2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.periodStart").value("2026-09-26"))
                .andExpect(jsonPath("$.data.periodEnd").value("2026-10-25"))
                .andExpect(jsonPath("$.data.dueAt").value("2026-10-25T18:00:00"))
                .andExpect(jsonPath("$.data.dDay").value(6))
                .andExpect(jsonPath("$.data.status").value("SCHEDULED"))
                .andExpect(jsonPath("$.data.billedAmount").value(79500))
                .andExpect(jsonPath("$.data.paymentCount").value(14))
                .andExpect(jsonPath("$.data.availableAmount").value(73000))
                .andExpect(jsonPath("$.data.carryoverAmount").value(59900))
                .andExpect(jsonPath("$.data.shortfallAmount").value(6500))
                .andExpect(jsonPath("$.data.surplusAmount").value(0))
                .andExpect(jsonPath("$.data.cardLinked").value(true));
    }

    @Test
    void 부모가_호출하면_403() throws Exception {
        mockMvc.perform(get("/settlements/current").header("X-Persona-Id", 1))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("자녀만 조회할 수 있어요"));
    }

    @Test
    void 진행_중인_정산이_없는_가족B는_404() throws Exception {
        mockMvc.perform(get("/settlements/current").header("X-Persona-Id", 4))
                .andExpect(status().isNotFound());
    }

    @Test
    void 없는_페르소나는_404() throws Exception {
        mockMvc.perform(get("/settlements/current").header("X-Persona-Id", 99))
                .andExpect(status().isNotFound());
    }

    @Test
    void 헤더가_숫자가_아니면_공통_형식의_400() throws Exception {
        mockMvc.perform(get("/settlements/current").header("X-Persona-Id", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void 헤더가_없으면_400() throws Exception {
        mockMvc.perform(get("/settlements/current"))
                .andExpect(status().isBadRequest());
    }

    // 계좌 전체 잔액(balance) - 지난달까지 남은 돈(carryover_balance) = 이번 달 쓸 수 있는 돈(용돈 + 미션 보상)
    @Test
    void 계좌_잔액에서_남은_돈을_빼면_이번_달_쓸_수_있는_돈과_같다() {
        Integer diff = jdbc.queryForObject(
                "SELECT balance - carryover_balance FROM accounts WHERE member_id = 2", Integer.class);
        assertEquals(73000, diff);
    }

    @Test
    void 월별_정산_이력은_시드_개수만큼_들어간다() {
        // 가족 A 12개(진행 중 1 포함), 가족 B 27개
        assertEquals(12, jdbc.queryForObject("SELECT COUNT(*) FROM settlements WHERE family_id = 1", Integer.class));
        assertEquals(27, jdbc.queryForObject("SELECT COUNT(*) FROM settlements WHERE family_id = 2", Integer.class));
    }
}
