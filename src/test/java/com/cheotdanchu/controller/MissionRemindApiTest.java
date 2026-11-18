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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 시드 기준: 제출 5 = 영수증 미션 확인 대기(PENDING, 재요청 알림은 어제 10/18에 보냄), 제출 1 = 분리수거 승인 완료. 테스트마다 롤백 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class MissionRemindApiTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    EntityManager em;

    @Test
    void 확인_대기_제출을_부모에게_다시_알린다() throws Exception {
        int before = jdbc.queryForObject("SELECT COUNT(*) FROM notifications WHERE member_id = 1 AND type = 'MISSION_REMIND'", Integer.class);

        mockMvc.perform(post("/missions/submissions/5/remind").header("X-Persona-Id", 2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("부모님께 다시 알림을 보냈어요."));
        em.flush();

        assertEquals(before + 1, jdbc.queryForObject("SELECT COUNT(*) FROM notifications WHERE member_id = 1 AND type = 'MISSION_REMIND'", Integer.class));
        Map<String, Object> n = jdbc.queryForMap(
                "SELECT title, body, ref_id FROM notifications WHERE member_id = 1 AND type = 'MISSION_REMIND' ORDER BY notification_id DESC LIMIT 1");
        assertEquals("루피가 미션 확인을 기다려요", n.get("title"));
        assertEquals("이번 주 영수증 정리하기", n.get("body"));
        assertEquals(5, ((Number) n.get("ref_id")).intValue());
    }

    @Test
    void 같은_제출은_하루에_한_번만_다시_알린다() throws Exception {
        mockMvc.perform(post("/missions/submissions/5/remind").header("X-Persona-Id", 2)).andExpect(status().isOk());

        mockMvc.perform(post("/missions/submissions/5/remind").header("X-Persona-Id", 2))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("오늘은 이미 부모님께 알림을 보냈어요."));
    }

    @Test
    void 이미_확인한_제출은_409() throws Exception {
        mockMvc.perform(post("/missions/submissions/1/remind").header("X-Persona-Id", 2))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("이미 부모님이 확인한 미션이에요."));
    }

    @Test
    void 부모는_403_다른_가족은_404() throws Exception {
        mockMvc.perform(post("/missions/submissions/5/remind").header("X-Persona-Id", 1)).andExpect(status().isForbidden());
        mockMvc.perform(post("/missions/submissions/5/remind").header("X-Persona-Id", 4)).andExpect(status().isNotFound());
    }
}
