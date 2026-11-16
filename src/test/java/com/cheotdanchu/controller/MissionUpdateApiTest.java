package com.cheotdanchu.controller;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 시드 기준: 미션 3 = 설거지 일주일(NEW, 10/19~10/25, 보상 3,000원). 가족 A 이번 달 남은 한도 18,000원. 테스트마다 롤백 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class MissionUpdateApiTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    EntityManager em;

    private ResultActions update(int personaId, long missionId, String body) throws Exception {
        return mockMvc.perform(patch("/missions/" + missionId).header("X-Persona-Id", personaId)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    @Test
    void 보낸_필드만_바꾼다() throws Exception {
        update(1, 3, "{\"title\":\"설거지 열흘\",\"rewardAmount\":3500,\"endDate\":\"2026-10-28\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("미션을 수정했어요."))
                .andExpect(jsonPath("$.data.missionId").value(3));
        em.flush();

        Map<String, Object> m = jdbc.queryForMap("SELECT title, criteria, reward_amount, end_date FROM missions WHERE mission_id = 3");
        assertEquals("설거지 열흘", m.get("title"));
        assertEquals("매일 저녁 설거지 후 사진 인증", m.get("criteria"));
        assertEquals(3500, ((Number) m.get("reward_amount")).intValue());
        assertEquals("2026-10-28", m.get("end_date").toString());
    }

    @Test
    void 끝난_미션은_409() throws Exception {
        jdbc.update("UPDATE missions SET status = 'COMPLETED' WHERE mission_id = 3");

        update(1, 3, "{\"title\":\"바꾸기\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("끝난 미션은 수정할 수 없어요."));
    }

    @Test
    void 아직_열지_않은_제안은_409() throws Exception {
        update(1, 4, "{\"title\":\"바꾸기\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("아직 열지 않은 미션은 '열기'에서 정해 주세요."));
    }

    @Test
    void 확인_대기_중에는_보상을_바꿀_수_없고_이름은_바꿀_수_있다() throws Exception {
        update(1, 2, "{\"rewardAmount\":500}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("아이가 낸 완료를 먼저 확인한 뒤 보상을 바꿔 주세요."));
        update(1, 2, "{\"title\":\"영수증 정리\"}").andExpect(status().isOk());
    }

    @Test
    void 마감일은_오늘보다_앞설_수_없다() throws Exception {
        jdbc.update("UPDATE missions SET start_date = '2026-10-01' WHERE mission_id = 3");

        update(1, 3, "{\"endDate\":\"2026-10-15\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("마감일은 오늘 이후로 정해 주세요."));
    }

    @Test
    void 마감일을_당겨서_목표_횟수를_이미_채웠으면_완료로_바꾼다() throws Exception {
        // 매일 미션 10/14~10/31, 이미 6번 승인 → 마감일을 10/19로 당기면 목표 6회
        jdbc.update("UPDATE missions SET status = 'IN_PROGRESS', start_date = '2026-10-14' WHERE mission_id = 3");
        for (int i = 0; i < 6; i++) {
            jdbc.update("INSERT INTO mission_submissions (mission_id, review_status, submitted_at, reviewed_at) VALUES (3, 'APPROVED', '2026-10-15 19:00:00', '2026-10-15 21:00:00')");
        }

        update(1, 3, "{\"endDate\":\"2026-10-19\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"));
    }

    @Test
    void 마감일을_당겨도_목표가_남아_있으면_진행_중() throws Exception {
        jdbc.update("UPDATE missions SET status = 'IN_PROGRESS', start_date = '2026-10-14' WHERE mission_id = 3");
        jdbc.update("INSERT INTO mission_submissions (mission_id, review_status, submitted_at, reviewed_at) VALUES (3, 'APPROVED', '2026-10-15 19:00:00', '2026-10-15 21:00:00')");

        update(1, 3, "{\"endDate\":\"2026-10-20\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"));
    }

    @Test
    void 보상이_남은_한도를_넘으면_422() throws Exception {
        update(1, 3, "{\"rewardAmount\":20000}")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.message").value("이번 달 남은 한도(18,000원)를 넘어요."));
    }

    @Test
    void 마감일이_시작일보다_앞서면_400() throws Exception {
        jdbc.update("UPDATE missions SET start_date = '2026-10-25', end_date = '2026-10-31' WHERE mission_id = 3");

        update(1, 3, "{\"endDate\":\"2026-10-20\"}").andExpect(status().isBadRequest());
    }

    @Test
    void 바꿀_내용이_없거나_이름이_비어_있으면_400() throws Exception {
        update(1, 3, "{}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("바꿀 내용을 보내 주세요."));
        update(1, 3, "{\"title\":\"  \"}").andExpect(status().isBadRequest());
        update(1, 3, "{\"rewardAmount\":0}").andExpect(status().isBadRequest());
    }

    @Test
    void 자녀는_403_다른_가족은_404() throws Exception {
        update(2, 3, "{\"title\":\"바꾸기\"}").andExpect(status().isForbidden());
        update(3, 3, "{\"title\":\"바꾸기\"}").andExpect(status().isNotFound());
    }
}
