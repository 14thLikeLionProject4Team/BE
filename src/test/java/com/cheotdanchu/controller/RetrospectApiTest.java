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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 시드 기준: 가족 A는 끝난 정산이 있고, 월말에 전하기로 한 말(MONTHLY) 1건. 테스트마다 롤백 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class RetrospectApiTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    EntityManager em;

    @Test
    void 가장_최근에_끝난_정산의_회고_질문과_월말에_전할_말을_준다() throws Exception {
        Long latest = jdbc.queryForObject(
                "SELECT settlement_id FROM settlements WHERE family_id = 1 AND status = 'COMPLETED' AND is_excluded = FALSE ORDER BY period_end DESC LIMIT 1", Long.class);

        mockMvc.perform(get("/retrospects/current").header("X-Persona-Id", 2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("월말 회고 조회 성공"))
                .andExpect(jsonPath("$.data.available").value(true))
                .andExpect(jsonPath("$.data.settlementId").value(latest))
                .andExpect(jsonPath("$.data.questions.length()").value(2))
                .andExpect(jsonPath("$.data.pendingMessages.length()").value(1))
                .andExpect(jsonPath("$.data.pendingMessages[0].request").value("학원 가는 버스비가 생각보다 많이 나와요"))
                .andExpect(jsonPath("$.data.pendingMessages[0].reason").value("학원이 멀어져서 버스를 더 타요"));
    }

    @Test
    void 끝난_정산이_없으면_available_false() throws Exception {
        jdbc.update("UPDATE settlements SET status = 'SCHEDULED' WHERE family_id = 1");

        mockMvc.perform(get("/retrospects/current").header("X-Persona-Id", 2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.available").value(false))
                .andExpect(jsonPath("$.data.settlementId").value(nullValue()))
                .andExpect(jsonPath("$.data.pendingMessages.length()").value(0));
    }

    @Test
    void 부모가_호출하면_403() throws Exception {
        mockMvc.perform(get("/retrospects/current").header("X-Persona-Id", 1))
                .andExpect(status().isForbidden());
    }

    private long insertMonthly(String request) {
        jdbc.update("INSERT INTO child_messages (family_id, request, status) VALUES (1, ?, 'MONTHLY')", request);
        return jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    private ResultActions approve(int personaId, String body) throws Exception {
        return mockMvc.perform(post("/retrospects/current/approve").header("X-Persona-Id", personaId)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    @Test
    void 고른_말은_부모에게_가고_고르지_않은_말은_버린다() throws Exception {
        long chosen = insertMonthly("다음 달에 문제집을 사야 해요");
        long seeded = jdbc.queryForObject("SELECT child_message_id FROM child_messages WHERE family_id = 1 AND status = 'MONTHLY' AND child_message_id <> ?", Long.class, chosen);

        approve(2, "{\"approvedIds\": [" + chosen + "]}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("부모님께 전했어요."))
                .andExpect(jsonPath("$.data.sentCount").value(1))
                .andExpect(jsonPath("$.data.discardedCount").value(1));
        em.flush();

        Map<String, Object> sent = jdbc.queryForMap("SELECT status, delivery, sent_at FROM child_messages WHERE child_message_id = ?", chosen);
        assertEquals("SENT", sent.get("status"));
        assertEquals("MONTHLY", sent.get("delivery"));
        assertEquals("DISCARDED", jdbc.queryForObject("SELECT status FROM child_messages WHERE child_message_id = ?", String.class, seeded));

        mockMvc.perform(get("/parent/decisions").header("X-Persona-Id", 1))
                .andExpect(jsonPath("$.data.cards[?(@.refId == " + chosen + ")].label").value("월말 회고에서 전한 말"));
        mockMvc.perform(get("/retrospects/current").header("X-Persona-Id", 2))
                .andExpect(jsonPath("$.data.pendingMessages.length()").value(0));
    }

    @Test
    void 아무것도_고르지_않으면_모두_버린다() throws Exception {
        approve(2, "{\"approvedIds\": []}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("이번 달은 전하지 않았어요."))
                .andExpect(jsonPath("$.data.sentCount").value(0))
                .andExpect(jsonPath("$.data.discardedCount").value(1));
    }

    @Test
    void 월말에_전하기로_한_말이_아니면_400() throws Exception {
        Long sentSeed = jdbc.queryForObject("SELECT child_message_id FROM child_messages WHERE family_id = 1 AND status = 'SENT'", Long.class);

        approve(2, "{\"approvedIds\": [" + sentSeed + "]}").andExpect(status().isBadRequest());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM child_messages WHERE family_id = 1 AND status = 'MONTHLY'", Integer.class));
    }

    @Test
    void 끝난_정산이_없으면_409() throws Exception {
        jdbc.update("UPDATE settlements SET status = 'SCHEDULED' WHERE family_id = 1");

        approve(2, "{\"approvedIds\": []}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("결제일 정산이 끝난 뒤에 회고할 수 있어요."));
    }
}
