package com.cheotdanchu.controller;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 시드 기준: 제출 5 = 미션 2(영수증, 한 번, 3,000원)의 확인 대기 제출. 미션 1(분리수거, 매주, 목표 5회 중 4회 승인).
 * 가족 A 남은 한도 18,000원. 테스트마다 롤백.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class MissionReviewApiTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    EntityManager em;

    private int childBalance() {
        return jdbc.queryForObject("SELECT balance FROM accounts WHERE member_id = 2", Integer.class);
    }

    /** 미션을 확인 대기로 만들고 확인 대기 제출을 하나 넣는다 */
    private Long pendingSubmission(long missionId) {
        jdbc.update("UPDATE missions SET status = 'SUBMITTED' WHERE mission_id = ?", missionId);
        jdbc.update("INSERT INTO mission_submissions (mission_id, review_status, submitted_at) VALUES (?, 'PENDING', '2026-10-19 20:00:00')", missionId);
        return jdbc.queryForObject("SELECT MAX(submission_id) FROM mission_submissions WHERE mission_id = ?", Long.class, missionId);
    }

    @Test
    void 승인하면_보상이_자녀_계좌로_가고_한_번짜리_미션은_완료된다() throws Exception {
        int before = childBalance();

        mockMvc.perform(post("/missions/submissions/5/approve").header("X-Persona-Id", 1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("3,000원을 보냈어요."))
                .andExpect(jsonPath("$.data.reviewStatus").value("APPROVED"))
                .andExpect(jsonPath("$.data.missionStatus").value("COMPLETED"))
                .andExpect(jsonPath("$.data.rewardAmount").value(3000));

        em.flush();
        assertEquals(before + 3000, childBalance());
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM transfers WHERE type = 'MISSION_REWARD' AND mission_id = 2", Integer.class));
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM notifications WHERE member_id = 2 AND type = 'MISSION_REWARD'", Integer.class));
    }

    @Test
    void 반복_미션은_목표_횟수를_채워야_완료된다() throws Exception {
        // 분리수거: 4회 승인 + 이번 승인 = 5회 = 목표 → 완료
        Long fifth = pendingSubmission(1);
        mockMvc.perform(post("/missions/submissions/" + fifth + "/approve").header("X-Persona-Id", 1))
                .andExpect(jsonPath("$.data.missionStatus").value("COMPLETED"));

        // 설거지(매일, 목표 7회): 첫 승인 → 다음 회차를 위해 진행 중
        Long first = pendingSubmission(3);
        mockMvc.perform(post("/missions/submissions/" + first + "/approve").header("X-Persona-Id", 1))
                .andExpect(jsonPath("$.data.missionStatus").value("IN_PROGRESS"));
    }

    @Test
    void 지급할_때_이번_정산_기간_한도를_넘으면_422() throws Exception {
        jdbc.update("UPDATE missions SET reward_amount = 18001 WHERE mission_id = 2");

        mockMvc.perform(post("/missions/submissions/5/approve").header("X-Persona-Id", 1))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.message").value("이번 달 남은 한도(18,000원)를 넘어요."));
    }

    @Test
    void 이미_처리된_제출은_409() throws Exception {
        mockMvc.perform(post("/missions/submissions/1/approve").header("X-Persona-Id", 1)) // 시드에서 이미 승인됨
                .andExpect(status().isConflict());
    }

    @Test
    void 반려하면_보상_없이_진행_중으로_돌아간다() throws Exception {
        int before = childBalance();

        mockMvc.perform(post("/missions/submissions/5/reject").header("X-Persona-Id", 1)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"PHOTO_UNCLEAR\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reviewStatus").value("REJECTED"))
                .andExpect(jsonPath("$.data.rejectReason").value("PHOTO_UNCLEAR"))
                .andExpect(jsonPath("$.data.missionStatus").value("IN_PROGRESS"));

        em.flush();
        assertEquals(before, childBalance());
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM notifications WHERE member_id = 2 AND type = 'MISSION_REJECTED'", Integer.class));
    }

    @Test
    void 반려_사유가_없으면_400() throws Exception {
        mockMvc.perform(post("/missions/submissions/5/reject").header("X-Persona-Id", 1)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 자녀가_승인하면_403() throws Exception {
        mockMvc.perform(post("/missions/submissions/5/approve").header("X-Persona-Id", 2))
                .andExpect(status().isForbidden());
    }

    @Test
    void 다른_가족의_제출이면_404() throws Exception {
        mockMvc.perform(post("/missions/submissions/5/approve").header("X-Persona-Id", 3))
                .andExpect(status().isNotFound());
    }
}
