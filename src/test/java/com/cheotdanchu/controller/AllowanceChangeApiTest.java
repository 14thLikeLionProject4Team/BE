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

import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 시드 기준: 가족 A(루피 엄마 id 1, 루피 id 2) 현재 용돈 70,000원, 대기 중인 제안 1번. 테스트마다 롤백된다 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AllowanceChangeApiTest {

    /** 가족 A 의 가장 최근 용돈 변경 이력에서 고른 열을 읽는 쿼리 */
    private static final String LATEST_CHANGE =
            "SELECT %s FROM allowance_changes WHERE family_id = 1 ORDER BY effective_from DESC, change_id DESC LIMIT 1";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    EntityManager em;

    private ResultActions save(int personaId, String body) throws Exception {
        return mockMvc.perform(post("/allowance/changes").header("X-Persona-Id", personaId)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private static String body(int amount, String reason, int allowanceDay, int settlementDay, Long suggestionId) {
        return "{\"amount\":" + amount + (reason == null ? "" : ",\"reason\":\"" + reason + "\"")
                + ",\"allowanceDay\":" + allowanceDay + ",\"autoTransfer\":false,\"settlementDay\":" + settlementDay
                + (suggestionId == null ? "" : ",\"suggestionId\":" + suggestionId) + "}";
    }

    @Test
    void 저장하면_다음_달_1일부터의_이력과_설정과_아이_알림이_생긴다() throws Exception {
        save(1, body(72000, "SCHOOL", 5, 20, null))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("용돈을 저장했어요"))
                .andExpect(jsonPath("$.data.prevAmount").value(70000))
                .andExpect(jsonPath("$.data.amount").value(72000))
                .andExpect(jsonPath("$.data.effectiveFrom").value("2026-11-01"))
                .andExpect(jsonPath("$.data.notifiedChild").value(true));
        em.flush();

        Map<String, Object> change = jdbc.queryForMap(
                LATEST_CHANGE.formatted("amount, reason, source"));
        assertEquals(72000, ((Number) change.get("amount")).intValue());
        assertEquals("SCHOOL", change.get("reason"));
        assertEquals("PARENT", change.get("source"));

        Map<String, Object> setting = jdbc.queryForMap(
                "SELECT allowance_day, auto_transfer, settlement_day FROM family_settings WHERE family_id = 1");
        assertEquals(5, ((Number) setting.get("allowance_day")).intValue());
        assertEquals(false, setting.get("auto_transfer"));
        assertEquals(20, ((Number) setting.get("settlement_day")).intValue());

        assertEquals("11월부터 용돈이 72,000원이 돼요.", jdbc.queryForObject(
                "SELECT body FROM notifications WHERE member_id = 2 AND type = 'ALLOWANCE_CHANGED'", String.class));
        // 대기 중인 제안은 그대로다 (제안과 무관한 저장)
        assertEquals("PENDING", jdbc.queryForObject("SELECT status FROM allowance_suggestions WHERE suggestion_id = 1", String.class));
    }

    @Test
    void 이유는_선택이라_없어도_저장된다() throws Exception {
        save(1, body(72000, null, 1, 25, null)).andExpect(status().isCreated());
    }

    @Test
    void AI_제안에서_온_저장은_제안을_적용_처리하고_출처가_AI_제안이다() throws Exception {
        save(1, body(73000, "INFLATION", 1, 25, 1L))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.amount").value(73000));
        em.flush();

        assertEquals("AI_SUGGESTION", jdbc.queryForObject(
                LATEST_CHANGE.formatted("source"), String.class));
        assertEquals("APPLIED", jdbc.queryForObject("SELECT status FROM allowance_suggestions WHERE suggestion_id = 1", String.class));
        assertEquals("11월부터 용돈이 73,000원이 돼요. 물가가 올라서 부모님이 올렸어요.", jdbc.queryForObject(
                "SELECT body FROM notifications WHERE member_id = 2 AND type = 'ALLOWANCE_CHANGED'", String.class));
    }

    @Test
    void 이미_처리한_제안으로_저장하면_409이고_아무것도_저장되지_않는다() throws Exception {
        save(1, body(73000, "INFLATION", 1, 25, 1L)).andExpect(status().isCreated());

        save(1, body(74000, "INFLATION", 1, 25, 1L))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("이미 처리한 제안이에요."));
    }

    @Test
    void 다른_가족의_제안이거나_없는_제안이면_404() throws Exception {
        save(3, body(80000, null, 1, 25, 1L)).andExpect(status().isNotFound());
        save(1, body(73000, null, 1, 25, 999L)).andExpect(status().isNotFound());
    }

    @Test
    void 자녀가_저장하면_403() throws Exception {
        save(2, body(72000, null, 1, 25, null)).andExpect(status().isForbidden());
    }

    @Test
    void 값이_잘못되면_400() throws Exception {
        save(1, body(0, null, 1, 25, null)).andExpect(status().isBadRequest());
        save(1, body(72000, null, 0, 25, null)).andExpect(status().isBadRequest());
        save(1, body(72000, null, 29, 25, null)).andExpect(status().isBadRequest());
        save(1, body(72000, null, 1, 29, null)).andExpect(status().isBadRequest());
        save(1, body(72000, "INITIAL", 1, 25, null)).andExpect(status().isBadRequest());
        save(1, body(72000, "MAYBE", 1, 25, null)).andExpect(status().isBadRequest());
        save(1, "{\"amount\":72000,\"allowanceDay\":1,\"settlementDay\":25}").andExpect(status().isBadRequest());
        save(1, "{}").andExpect(status().isBadRequest());
    }

    @Test
    void 가족_B도_자기_가족만_바꾼다() throws Exception {
        save(3, body(50000, "ETC", 3, 25, null)).andExpect(status().isCreated());
        em.flush();
        assertEquals(1, jdbc.queryForObject("SELECT allowance_day FROM family_settings WHERE family_id = 1", Integer.class));
    }

    @Test
    void 같은_달에_두_번_저장하면_나중에_저장한_금액이_적용_중이고_이전_금액이_이어진다() throws Exception {
        save(1, body(72000, "SCHOOL", 1, 25, null)).andExpect(status().isCreated());
        save(1, body(74000, "SCHOOL", 1, 25, null))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.prevAmount").value(72000));
        // 74,000원이 정해진 값이므로 같은 금액으로 또 저장하면 바뀐 게 없다 (옛 72,000원이 읽히면 새 이력이 생긴다)
        save(1, body(74000, "SCHOOL", 1, 25, null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.notifiedChild").value(false));
    }

    @Test
    void 제안과_다른_금액으로_저장하면_직접_정한_것이고_제안은_쓰지_않은_것으로_닫는다() throws Exception {
        save(1, body(72000, "INFLATION", 1, 25, 1L)).andExpect(status().isCreated());
        em.flush();

        assertEquals("PARENT", jdbc.queryForObject(
                LATEST_CHANGE.formatted("source"), String.class));
        assertEquals("KEPT", jdbc.queryForObject("SELECT status FROM allowance_suggestions WHERE suggestion_id = 1", String.class));
    }

    @Test
    void AI_제안_그대로_저장할_때_이유를_안_보내면_물가로_남는다() throws Exception {
        save(1, body(73000, null, 1, 25, 1L)).andExpect(status().isCreated());
        em.flush();

        assertEquals("INFLATION", jdbc.queryForObject(
                LATEST_CHANGE.formatted("reason"), String.class));
        assertEquals("11월부터 용돈이 73,000원이 돼요. 물가가 올라서 부모님이 올렸어요.", jdbc.queryForObject(
                "SELECT body FROM notifications WHERE member_id = 2 AND type = 'ALLOWANCE_CHANGED'", String.class));
    }

    @Test
    void 금액이_그대로이고_날짜만_바꾸면_설정만_저장되고_이력과_알림은_없다() throws Exception {
        save(1, body(70000, null, 7, 20, null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.changeId").value(nullValue()))
                .andExpect(jsonPath("$.data.prevAmount").value(70000))
                .andExpect(jsonPath("$.data.amount").value(70000))
                .andExpect(jsonPath("$.data.effectiveFrom").value("2026-11-01"))
                .andExpect(jsonPath("$.data.notifiedChild").value(false));
        em.flush();

        assertEquals(7, jdbc.queryForObject("SELECT allowance_day FROM family_settings WHERE family_id = 1", Integer.class));
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM allowance_changes WHERE family_id = 1", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM notifications WHERE member_id = 2", Integer.class));
    }

    @Test
    void 용돈이_이미_바뀌어_오래된_제안으로는_저장할_수_없고_설정도_바뀌지_않는다() throws Exception {
        jdbc.update("INSERT INTO allowance_changes (family_id, prev_amount, amount, effective_from, reason, source, created_at) "
                + "VALUES (1, 70000, 72000, '2026-11-01', 'ETC', 'PARENT', '2026-10-19 10:00:00')");

        save(1, body(73000, "INFLATION", 9, 25, 1L))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("용돈이 이미 바뀌어서 이 제안은 쓸 수 없어요."));
        em.flush();
        assertEquals(1, jdbc.queryForObject("SELECT allowance_day FROM family_settings WHERE family_id = 1", Integer.class));
    }
}
