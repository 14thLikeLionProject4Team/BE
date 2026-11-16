package com.cheotdanchu.controller;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 시드 기준: 미션 5 = 자녀 화면 추천(가격 비교 장보기, 3,500원), 8 = 부모 화면 추천, 4 = 아이 제안. 테스트마다 롤백 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class MissionRecommendationProposeApiTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    EntityManager em;

    @Test
    void 추천을_제안하면_PROPOSED가_되고_부모에게_알린다() throws Exception {
        mockMvc.perform(post("/missions/5/propose").header("X-Persona-Id", 2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("부모님께 미션을 제안했어요."))
                .andExpect(jsonPath("$.data.missionId").value(5))
                .andExpect(jsonPath("$.data.status").value("PROPOSED"));
        em.flush();

        Map<String, Object> m = jdbc.queryForMap("SELECT reward_amount, desired_reward FROM missions WHERE mission_id = 5");
        assertNull(m.get("reward_amount"));
        assertEquals(3500, ((Number) m.get("desired_reward")).intValue());
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM notifications WHERE member_id = 1 AND type = 'MISSION_PROPOSED' AND ref_id = 5", Integer.class));

        // 추천 목록에서 빠지고 제안 목록에 나온다
        mockMvc.perform(get("/missions/recommendations").param("target", "CHILD").header("X-Persona-Id", 2))
                .andExpect(jsonPath("$.data.recommendations[?(@.missionId == 5)]").isEmpty());
        mockMvc.perform(get("/missions").param("status", "PROPOSED").header("X-Persona-Id", 1))
                .andExpect(jsonPath("$.data.missions[?(@.missionId == 5)]").isNotEmpty());
    }

    @Test
    void 이미_제안했거나_부모_화면_추천이면_409() throws Exception {
        mockMvc.perform(post("/missions/5/propose").header("X-Persona-Id", 2)).andExpect(status().isOk());

        mockMvc.perform(post("/missions/5/propose").header("X-Persona-Id", 2))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("이미 제안했거나 제안할 수 없는 미션이에요."));
        mockMvc.perform(post("/missions/8/propose").header("X-Persona-Id", 2)).andExpect(status().isConflict());
        mockMvc.perform(post("/missions/4/propose").header("X-Persona-Id", 2)).andExpect(status().isConflict());
    }

    @Test
    void 부모는_403_다른_가족은_404() throws Exception {
        mockMvc.perform(post("/missions/5/propose").header("X-Persona-Id", 1)).andExpect(status().isForbidden());
        mockMvc.perform(post("/missions/5/propose").header("X-Persona-Id", 4)).andExpect(status().isNotFound());
    }
}
