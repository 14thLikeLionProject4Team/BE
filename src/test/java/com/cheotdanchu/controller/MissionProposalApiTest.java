package com.cheotdanchu.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 시드 기준: 가족 A. 테스트마다 롤백 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class MissionProposalApiTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbc;

    private static final String PROPOSAL = """
            {"title":"강아지 산책","missionType":"CHORE","desiredReward":2000,
             "startDate":"2026-10-19","endDate":"2026-10-25","repeatType":"DAILY","verifyMethod":"PHOTO"}
            """;

    @Test
    void 자녀가_제안하면_PROPOSED로_저장되고_부모에게_알림이_간다() throws Exception {
        mockMvc.perform(post("/missions/proposals").header("X-Persona-Id", 2)
                        .contentType(MediaType.APPLICATION_JSON).content(PROPOSAL))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("부모님께 미션을 제안했어요."))
                .andExpect(jsonPath("$.data.status").value("PROPOSED"));

        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM notifications WHERE member_id = 1 AND type = 'MISSION_PROPOSED'", Integer.class));
        mockMvc.perform(get("/missions").param("status", "PROPOSED").header("X-Persona-Id", 1))
                .andExpect(jsonPath("$.data.count").value(2));
    }

    @Test
    void 부모가_제안하면_403_기간이_거꾸로면_400() throws Exception {
        mockMvc.perform(post("/missions/proposals").header("X-Persona-Id", 1)
                        .contentType(MediaType.APPLICATION_JSON).content(PROPOSAL))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/missions/proposals").header("X-Persona-Id", 2)
                        .contentType(MediaType.APPLICATION_JSON).content(PROPOSAL.replace("2026-10-25", "2026-10-01")))
                .andExpect(status().isBadRequest());
    }

}
