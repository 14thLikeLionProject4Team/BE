package com.cheotdanchu.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 시드 기준: 미션 1 진행 중, 2 확인 대기(SUBMITTED), 3 새 미션(NEW), 4 아이 제안. 테스트마다 롤백 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class MissionCancelApiTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void 취소하면_CANCELED가_되고_종료_목록에_남는다() throws Exception {
        mockMvc.perform(delete("/missions/3").header("X-Persona-Id", 1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("미션을 취소했어요."))
                .andExpect(jsonPath("$.data.missionId").value(3))
                .andExpect(jsonPath("$.data.status").value("CANCELED"));

        mockMvc.perform(get("/missions").param("status", "ended").header("X-Persona-Id", 1))
                .andExpect(jsonPath("$.data.missions[?(@.missionId == 3)].status").value("CANCELED"));
        mockMvc.perform(get("/missions").param("status", "active").header("X-Persona-Id", 1))
                .andExpect(jsonPath("$.data.missions[?(@.missionId == 3)]").isEmpty());
    }

    @Test
    void 확인_대기_중이면_제출을_먼저_처리해야_한다() throws Exception {
        mockMvc.perform(delete("/missions/2").header("X-Persona-Id", 1))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("아이가 낸 완료를 먼저 확인해 주세요."));
    }

    @Test
    void 끝난_미션이나_제안은_409() throws Exception {
        jdbc.update("UPDATE missions SET status = 'COMPLETED' WHERE mission_id = 3");

        mockMvc.perform(delete("/missions/3").header("X-Persona-Id", 1))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("끝난 미션은 취소할 수 없어요."));
        mockMvc.perform(delete("/missions/4").header("X-Persona-Id", 1))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("아직 열지 않은 미션은 '열기'에서 정해 주세요."));
    }

    @Test
    void 자녀는_403_다른_가족은_404() throws Exception {
        mockMvc.perform(delete("/missions/3").header("X-Persona-Id", 2)).andExpect(status().isForbidden());
        mockMvc.perform(delete("/missions/3").header("X-Persona-Id", 3)).andExpect(status().isNotFound());
    }
}
