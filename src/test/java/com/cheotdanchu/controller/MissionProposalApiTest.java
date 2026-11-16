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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 시드 기준: 미션 4 = 아이가 제안한 화분 미션(PROPOSED), 미션 1 = 진행 중. 가족 A 남은 한도 18,000원. 테스트마다 롤백 */
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

    private String openBody(int reward) {
        return """
                {"title":"베란다 화분 물 주기","criteria":"일주일에 두 번","missionType":"CHORE","rewardAmount":%d,
                 "startDate":"2026-10-19","endDate":"2026-11-15","repeatType":"WEEKLY","verifyMethod":"PHOTO"}
                """.formatted(reward);
    }

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

    @Test
    void 부모가_제안을_금액_정해서_열면_NEW가_되고_출처는_아이_제안으로_남는다() throws Exception {
        mockMvc.perform(patch("/missions/4/open").header("X-Persona-Id", 1)
                        .contentType(MediaType.APPLICATION_JSON).content(openBody(3000)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("NEW"));

        mockMvc.perform(get("/missions/4").header("X-Persona-Id", 2))
                .andExpect(jsonPath("$.data.origin").value("CHILD"))
                .andExpect(jsonPath("$.data.rewardAmount").value(3000))
                .andExpect(jsonPath("$.data.desiredReward").value(3000));
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM notifications WHERE member_id = 2 AND type = 'NEW_MISSION'", Integer.class));
    }

    @Test
    void 열_때도_남은_한도를_넘으면_422() throws Exception {
        mockMvc.perform(patch("/missions/4/open").header("X-Persona-Id", 1)
                        .contentType(MediaType.APPLICATION_JSON).content(openBody(18001)))
                .andExpect(status().is(422));
    }

    @Test
    void 제안이_아닌_미션은_열_수_없다() throws Exception {
        mockMvc.perform(patch("/missions/1/open").header("X-Persona-Id", 1)
                        .contentType(MediaType.APPLICATION_JSON).content(openBody(3000)))
                .andExpect(status().isConflict());
    }

    @Test
    void 보류한_제안은_다시_보류할_수_없지만_열_수는_있다() throws Exception {
        mockMvc.perform(patch("/missions/4/hold").header("X-Persona-Id", 1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ON_HOLD"));

        mockMvc.perform(patch("/missions/4/hold").header("X-Persona-Id", 1))
                .andExpect(status().isConflict());
        mockMvc.perform(patch("/missions/4/open").header("X-Persona-Id", 1)
                        .contentType(MediaType.APPLICATION_JSON).content(openBody(3000)))
                .andExpect(jsonPath("$.data.status").value("NEW"));
    }

    @Test
    void 자녀는_보류할_수_없고_다른_가족은_404() throws Exception {
        mockMvc.perform(patch("/missions/4/hold").header("X-Persona-Id", 2))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/missions/4/hold").header("X-Persona-Id", 3))
                .andExpect(status().isNotFound());
    }
}
