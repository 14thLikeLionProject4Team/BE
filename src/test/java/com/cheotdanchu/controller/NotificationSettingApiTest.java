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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 가족 A: 루피 엄마(id 1), 루피(id 2). 가족 B: 뽀로로 엄마(id 3), 뽀로로(id 4). 시드에는 알림 설정 행이 없어 모두 기본값이다. 테스트마다 롤백된다 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class NotificationSettingApiTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    EntityManager em;

    private ResultActions settings(int personaId) throws Exception {
        return mockMvc.perform(get("/notification-settings").header("X-Persona-Id", personaId));
    }

    private ResultActions change(int personaId, String type, String body) throws Exception {
        return mockMvc.perform(patch("/notification-settings/" + type).header("X-Persona-Id", personaId)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions turn(int personaId, String type, boolean enabled) throws Exception {
        return change(personaId, type, "{\"enabled\":" + enabled + "}");
    }

    @Test
    void 자녀는_자기_알림_항목_여섯_개가_모두_켜져_있다() throws Exception {
        settings(2)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("조회 성공"))
                .andExpect(jsonPath("$.data.items.length()").value(6))
                .andExpect(jsonPath("$.data.items[0].type").value("SETTLEMENT_RESULT"))
                .andExpect(jsonPath("$.data.items[0].label").value("결제일 정산 결과"))
                .andExpect(jsonPath("$.data.items[0].enabled").value(true))
                .andExpect(jsonPath("$.data.items[1].type").value("SHORTFALL_EXPECTED"))
                .andExpect(jsonPath("$.data.items[1].enabled").value(true))
                .andExpect(jsonPath("$.data.items[2].type").value("ALLOWANCE_CHANGED"))
                .andExpect(jsonPath("$.data.items[3].type").value("NEW_MISSION"))
                .andExpect(jsonPath("$.data.items[4].type").value("MISSION_REWARD"))
                .andExpect(jsonPath("$.data.items[5].type").value("MISSION_REJECTED"))
                .andExpect(jsonPath("$.data.items[5].enabled").value(true));
    }

    @Test
    void 부모는_다른_항목을_보고_부족_예상만_기본으로_꺼져_있다() throws Exception {
        settings(1)
                .andExpect(jsonPath("$.data.items.length()").value(6))
                .andExpect(jsonPath("$.data.items[0].type").value("SETTLEMENT_RESULT"))
                .andExpect(jsonPath("$.data.items[0].enabled").value(true))
                .andExpect(jsonPath("$.data.items[1].type").value("SHORTFALL_EXPECTED"))
                .andExpect(jsonPath("$.data.items[1].label").value("부족 예상"))
                .andExpect(jsonPath("$.data.items[1].enabled").value(false)) // 아이에게 먼저 가는 알림이라 부모는 기본 꺼짐
                .andExpect(jsonPath("$.data.items[2].type").value("MISSION_PROPOSED"))
                .andExpect(jsonPath("$.data.items[3].type").value("MISSION_REMIND"))
                .andExpect(jsonPath("$.data.items[4].type").value("RISK_PAYMENT"))
                .andExpect(jsonPath("$.data.items[5].type").value("ADULT_TRANSITION"))
                .andExpect(jsonPath("$.data.items[5].enabled").value(true));
    }

    @Test
    void 항목을_끄면_조회에_반영되고_응답은_바뀐_값이다() throws Exception {
        turn(2, "SHORTFALL_EXPECTED", false)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("알림 설정을 바꿨어요"))
                .andExpect(jsonPath("$.data.type").value("SHORTFALL_EXPECTED"))
                .andExpect(jsonPath("$.data.enabled").value(false));

        settings(2)
                .andExpect(jsonPath("$.data.items[1].enabled").value(false))
                .andExpect(jsonPath("$.data.items[0].enabled").value(true)); // 다른 항목은 그대로
    }

    @Test
    void 부모가_부족_예상을_켤_수_있다() throws Exception {
        turn(1, "SHORTFALL_EXPECTED", true).andExpect(status().isOk());

        settings(1).andExpect(jsonPath("$.data.items[1].enabled").value(true));
    }

    @Test
    void 같은_항목을_다시_바꾸면_행을_새로_만들지_않고_고친다() throws Exception {
        turn(2, "NEW_MISSION", false);
        turn(2, "NEW_MISSION", true);
        turn(2, "NEW_MISSION", false);
        em.flush();

        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM notification_settings WHERE member_id = 2 AND type = 'NEW_MISSION'", Integer.class));
        assertEquals(false, jdbc.queryForObject("SELECT enabled FROM notification_settings WHERE member_id = 2 AND type = 'NEW_MISSION'", Boolean.class));
        settings(2).andExpect(jsonPath("$.data.items[3].enabled").value(false));
    }

    @Test
    void 내_설정만_바뀌고_다른_사람의_설정은_그대로다() throws Exception {
        turn(2, "SETTLEMENT_RESULT", false);

        settings(1).andExpect(jsonPath("$.data.items[0].enabled").value(true)); // 루피 엄마
        settings(4).andExpect(jsonPath("$.data.items[0].enabled").value(true)); // 다른 가족의 자녀
    }

    @Test
    void 알_수_없는_항목은_404다() throws Exception {
        // 뒤의 둘은 정산 결과 항목에 묶이는 알림 종류일 뿐 설정 항목이 아니다
        for (String type : new String[]{"NOPE", "shortfall_expected", "SHORTFALL_RESOLVED", "AUTO_DEDUCT"}) {
            turn(2, type, false)
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value("알 수 없는 알림 항목이에요"));
        }
    }

    @Test
    void 내_역할에_없는_항목은_404다() throws Exception {
        turn(2, "MISSION_PROPOSED", false).andExpect(status().isNotFound()); // 자녀에게 없는 항목
        turn(1, "NEW_MISSION", false).andExpect(status().isNotFound()); // 부모에게 없는 항목
    }

    @Test
    void enabled가_없거나_불리언이_아니면_400이다() throws Exception {
        change(2, "NEW_MISSION", "{}").andExpect(status().isBadRequest());
        change(2, "NEW_MISSION", "{\"enabled\":null}").andExpect(status().isBadRequest());
        change(2, "NEW_MISSION", "{\"enabled\":\"maybe\"}").andExpect(status().isBadRequest());
        change(2, "NEW_MISSION", "").andExpect(status().isBadRequest());
    }

    @Test
    void 없는_페르소나는_404_헤더가_없으면_400() throws Exception {
        settings(999).andExpect(status().isNotFound());
        turn(999, "NEW_MISSION", false).andExpect(status().isNotFound());
        mockMvc.perform(get("/notification-settings")).andExpect(status().isBadRequest());
    }
}
