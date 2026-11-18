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

/** 시드 기준: 가족 A 이번 정산 기간 용돈 70,000원, 미션 상한 30% = 21,000원 중 3,000원 받음 → 남은 한도 18,000원. 테스트마다 롤백 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class RewardGuideApiTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void 용돈의_3에서_8퍼센트와_남은_한도를_준다() throws Exception {
        for (int personaId : new int[]{1, 2}) {
            mockMvc.perform(get("/missions/reward-guide").header("X-Persona-Id", personaId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("보상 가이드 조회 성공"))
                    .andExpect(jsonPath("$.data.allowanceAmount").value(70000))
                    .andExpect(jsonPath("$.data.minRate").value(3))
                    .andExpect(jsonPath("$.data.maxRate").value(8))
                    .andExpect(jsonPath("$.data.minReward").value(2100))
                    .andExpect(jsonPath("$.data.maxReward").value(5600))
                    .andExpect(jsonPath("$.data.remainingCap").value(18000))
                    .andExpect(jsonPath("$.data.quickChips.length()").value(3));
        }
    }

    @Test
    void 남은_한도는_월_보상_합계와_같다() throws Exception {
        String summary = mockMvc.perform(get("/missions/rewards/summary").header("X-Persona-Id", 1))
                .andReturn().getResponse().getContentAsString();
        Number remaining = com.jayway.jsonpath.JsonPath.read(summary, "$.data.remainingAmount");

        mockMvc.perform(get("/missions/reward-guide").header("X-Persona-Id", 1))
                .andExpect(jsonPath("$.data.remainingCap").value(remaining.longValue()));
    }

    @Test
    void 참고_구간은_100원_단위로_내린다() throws Exception {
        // 72,500원 × 3% = 2,175 → 2,100, × 8% = 5,800
        jdbc.update("UPDATE allowance_changes SET amount = 72500 WHERE family_id = 1 AND amount = 70000");

        mockMvc.perform(get("/missions/reward-guide").header("X-Persona-Id", 1))
                .andExpect(jsonPath("$.data.allowanceAmount").value(72500))
                .andExpect(jsonPath("$.data.minReward").value(2100))
                .andExpect(jsonPath("$.data.maxReward").value(5800));
    }

    @Test
    void 참고_구간은_남은_한도를_넘지_않는다() throws Exception {
        // 상한 비율을 5%로 내리면 상한 3,500원 중 3,000원 받음 → 남은 한도 500원. 구간 2,100~5,600원 → 500~500원
        jdbc.update("UPDATE family_settings SET mission_cap_rate = 5 WHERE family_id = 1");

        mockMvc.perform(get("/missions/reward-guide").header("X-Persona-Id", 1))
                .andExpect(jsonPath("$.data.remainingCap").value(500))
                .andExpect(jsonPath("$.data.minReward").value(500))
                .andExpect(jsonPath("$.data.maxReward").value(500));
    }

    @Test
    void 용돈이_적어도_최소_추천은_100원() throws Exception {
        // 3,000원 × 3% = 90 → 100원, × 8% = 240 → 200원 (이번 기간 받은 보상은 지워서 남은 한도 900원)
        jdbc.update("UPDATE allowance_changes SET amount = 3000 WHERE family_id = 1 AND amount = 70000");
        jdbc.update("DELETE FROM transfers WHERE type = 'MISSION_REWARD'");

        mockMvc.perform(get("/missions/reward-guide").header("X-Persona-Id", 1))
                .andExpect(jsonPath("$.data.minReward").value(100))
                .andExpect(jsonPath("$.data.maxReward").value(200));
    }

    @Test
    void 남은_한도가_없으면_구간도_0원() throws Exception {
        jdbc.update("UPDATE family_settings SET mission_cap_rate = 4 WHERE family_id = 1");

        mockMvc.perform(get("/missions/reward-guide").header("X-Persona-Id", 1))
                .andExpect(jsonPath("$.data.remainingCap").value(0))
                .andExpect(jsonPath("$.data.minReward").value(0))
                .andExpect(jsonPath("$.data.maxReward").value(0));
    }

    @Test
    void 용돈_설정이_없으면_금액은_null() throws Exception {
        jdbc.update("DELETE FROM allowance_changes WHERE family_id = 1");

        mockMvc.perform(get("/missions/reward-guide").header("X-Persona-Id", 1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.allowanceAmount").value(nullValue()))
                .andExpect(jsonPath("$.data.minReward").value(nullValue()))
                .andExpect(jsonPath("$.data.remainingCap").value(nullValue()))
                .andExpect(jsonPath("$.data.minRate").value(3));
    }
}
