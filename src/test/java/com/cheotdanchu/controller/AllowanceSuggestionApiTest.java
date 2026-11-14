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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 시드 기준: 가족 A(루피 엄마 id 1, 루피 id 2)에게 대기 중인 제안 1번(70,000 → 73,000원), 가족 B(뽀로로 엄마 id 3)는 제안 없음.
 * 테스트마다 롤백돼서 처리한 결과가 남지 않는다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AllowanceSuggestionApiTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    EntityManager em;

    /** 제안 처리 요청. status 는 APPLIED / KEPT 등 본문에 넣을 값 */
    private ResultActions decide(int personaId, long suggestionId, String status) throws Exception {
        return mockMvc.perform(patch("/allowance/suggestions/" + suggestionId).header("X-Persona-Id", personaId)
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"" + status + "\"}"));
    }

    private ResultActions suggestionOf(int personaId) throws Exception {
        return mockMvc.perform(get("/allowance/suggestion").header("X-Persona-Id", personaId));
    }

    @Test
    void 부모는_대기_중인_제안과_근거를_본다() throws Exception {
        suggestionOf(1)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("AI 용돈 제안 조회 성공"))
                .andExpect(jsonPath("$.data.suggestionId").value(1))
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.currentAmount").value(70000))
                .andExpect(jsonPath("$.data.suggestedAmount").value(73000))
                .andExpect(jsonPath("$.data.rangeMin").value(70000))
                .andExpect(jsonPath("$.data.rangeMax").value(75000))
                .andExpect(jsonPath("$.data.inflationRate").value(4.2))
                .andExpect(jsonPath("$.data.basis[0].category").value("식사"))
                .andExpect(jsonPath("$.data.basis[0].monthlySpent").value(25400))
                .andExpect(jsonPath("$.data.basis[0].changeRate").value(5.1))
                .andExpect(jsonPath("$.data.basis[1].category").value("교통"))
                .andExpect(jsonPath("$.data.explanation").value("루피가 자주 결제하는 품목의 물가가 평균 4.2% 올랐어요."))
                .andExpect(jsonPath("$.data.createdAt").value("2026-10-01T00:00:00"));
    }

    @Test
    void 대기_중인_제안이_없으면_data가_null인_200() throws Exception {
        suggestionOf(3)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").value(nullValue()));
    }

    @Test
    void 자녀가_조회하면_403() throws Exception {
        suggestionOf(2)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("부모만 할 수 있어요"));
    }

    @Test
    void 적용하면_다음_달_1일부터의_용돈_변경_이력과_아이_알림이_생긴다() throws Exception {
        decide(1, 1, "APPLIED")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("11월부터 용돈이 73,000원으로 바뀌어요."))
                .andExpect(jsonPath("$.data.status").value("APPLIED"))
                .andExpect(jsonPath("$.data.amount").value(73000))
                .andExpect(jsonPath("$.data.effectiveFrom").value("2026-11-01"));
        em.flush(); // 테스트 트랜잭션 안이라 커밋이 없으니, 저장한 것을 DB에 반영한 뒤 확인한다

        Map<String, Object> change = jdbc.queryForMap(
                "SELECT prev_amount, amount, effective_from, reason, source FROM allowance_changes "
                        + "WHERE family_id = 1 ORDER BY effective_from DESC LIMIT 1");
        assertEquals(70000, ((Number) change.get("prev_amount")).intValue());
        assertEquals(73000, ((Number) change.get("amount")).intValue());
        assertEquals("2026-11-01", change.get("effective_from").toString());
        assertEquals("INFLATION", change.get("reason"));
        assertEquals("AI_SUGGESTION", change.get("source"));

        Map<String, Object> notification = jdbc.queryForMap(
                "SELECT type, body FROM notifications WHERE member_id = 2");
        assertEquals("ALLOWANCE_CHANGED", notification.get("type"));
        assertEquals("11월부터 용돈이 73,000원이 돼요. 물가가 올라서 부모님이 올렸어요.", notification.get("body"));

        // 처리한 제안은 더 이상 대기 중이 아니다
        suggestionOf(1).andExpect(jsonPath("$.data").value(nullValue()));
    }

    @Test
    void 유지하면_용돈은_그대로이고_이력과_알림은_생기지_않는다() throws Exception {
        decide(1, 1, "KEPT")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("지금 용돈을 유지해요."))
                .andExpect(jsonPath("$.data.status").value("KEPT"))
                .andExpect(jsonPath("$.data.amount").value(70000))
                .andExpect(jsonPath("$.data.effectiveFrom").value(nullValue()));
        em.flush();

        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM allowance_changes WHERE family_id = 1", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM notifications WHERE member_id = 2", Integer.class));
        suggestionOf(1).andExpect(jsonPath("$.data").value(nullValue()));
    }

    @Test
    void 이미_처리한_제안은_409() throws Exception {
        decide(1, 1, "KEPT").andExpect(status().isOk());

        decide(1, 1, "APPLIED")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("이미 처리한 제안이에요."));
    }

    @Test
    void 다른_가족의_제안이거나_없는_제안이면_404() throws Exception {
        decide(3, 1, "APPLIED").andExpect(status().isNotFound());
        decide(1, 999, "APPLIED").andExpect(status().isNotFound());
    }

    // 제안을 만든 뒤 부모가 다음 달 용돈을 직접 72,000원으로 정했다면, 옛 제안을 적용해 그 결정을 덮어쓰면 안 된다
    @Test
    void 제안이_만들어진_뒤_용돈이_바뀌었으면_숨기고_적용도_거절한다() throws Exception {
        jdbc.update("INSERT INTO allowance_changes (family_id, prev_amount, amount, effective_from, reason, source, created_at) "
                + "VALUES (1, 70000, 72000, '2026-11-01', 'ETC', 'PARENT', '2026-10-19 10:00:00')");

        suggestionOf(1)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(nullValue()));

        decide(1, 1, "APPLIED")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("용돈이 이미 바뀌어서 이 제안은 쓸 수 없어요."));
        em.flush();
        assertEquals(3, jdbc.queryForObject("SELECT COUNT(*) FROM allowance_changes WHERE family_id = 1", Integer.class)); // 위에서 넣은 1건만 늘었다
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM notifications WHERE member_id = 2", Integer.class));
    }

    // 근거 JSON에 빠진 값이 있어도 제안 카드는 보여야 한다 (그 항목만 빠진다)
    @Test
    void 근거에_빠진_값이_있어도_제안은_보인다() throws Exception {
        jdbc.update("UPDATE allowance_suggestions SET basis = '[{\"category\":\"식사\"},{\"category\":\"교통\",\"monthlySpent\":12000,\"changeRate\":2.3}]' "
                + "WHERE suggestion_id = 1");

        suggestionOf(1)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.suggestedAmount").value(73000))
                .andExpect(jsonPath("$.data.basis.length()").value(1))
                .andExpect(jsonPath("$.data.basis[0].category").value("교통"));
    }

    @Test
    void 자녀가_처리하면_403() throws Exception {
        decide(2, 1, "APPLIED").andExpect(status().isForbidden());
    }

    @Test
    void 처리_값이_없거나_대기로_보내면_400() throws Exception {
        mockMvc.perform(patch("/allowance/suggestions/1").header("X-Persona-Id", 1)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        decide(1, 1, "PENDING").andExpect(status().isBadRequest());
        decide(1, 1, "MAYBE").andExpect(status().isBadRequest());
    }
}
