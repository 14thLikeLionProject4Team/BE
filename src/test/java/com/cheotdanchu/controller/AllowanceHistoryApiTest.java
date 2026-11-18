package com.cheotdanchu.controller;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 가족 A: 루피 엄마(id 1), 가족 B: 뽀로로 엄마(id 3). 시연 기준일 2026-10-19.
 * 시드의 이력은 건드리지 않고 비교하고, 순서·페이지 테스트는 먼 미래 시각(2030년)의 이력을 직접 넣어서 쓴다. 테스트마다 롤백된다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AllowanceHistoryApiTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbc;

    private ResultActions history(int personaId, String query) throws Exception {
        return mockMvc.perform(get("/allowance/changes" + query).header("X-Persona-Id", personaId));
    }

    private long insertChange(int familyId, Integer prev, int amount, String reason, String source, String createdAt) {
        jdbc.update("INSERT INTO allowance_changes (family_id, prev_amount, amount, effective_from, reason, source, created_at) "
                + "VALUES (?, ?, ?, '2030-02-01', ?, ?, ?)", familyId, prev, amount, reason, source, createdAt);
        return jdbc.queryForObject("SELECT MAX(change_id) FROM allowance_changes", Long.class);
    }

    private List<Long> idsInDbOrder(int familyId) {
        return jdbc.queryForList("SELECT change_id FROM allowance_changes WHERE family_id = ? ORDER BY created_at DESC, change_id DESC",
                Long.class, familyId);
    }

    /** 페이지를 끝까지 따라가며 ID 를 모은다. 페이징 버그로 끝나지 않으면 멈추지 않고 실패하도록 반복 횟수에 상한을 둔다 */
    private List<Long> walk(int personaId, int size) throws Exception {
        List<Long> ids = new ArrayList<>();
        String cursor = null;
        int pages = 0;
        do {
            assertTrue(++pages <= 100, "페이지가 끝나지 않아요 (nextCursor 가 계속 이어져요)");
            String query = "?size=" + size + (cursor == null ? "" : "&cursor=" + cursor);
            String body = history(personaId, query).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            List<Number> page = JsonPath.read(body, "$.data.items[*].changeId");
            page.forEach(n -> ids.add(n.longValue()));
            cursor = JsonPath.read(body, "$.data.nextCursor");
        } while (cursor != null);
        return ids;
    }

    private static String cursorOf(String raw) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void 우리_가족의_이력을_최신순으로_모두_준다() throws Exception {
        List<Long> expected = idsInDbOrder(1);

        String body = history(1, "").andExpect(status().isOk()).andExpect(jsonPath("$.message").value("조회 성공")).andReturn().getResponse().getContentAsString();

        List<Number> ids = JsonPath.read(body, "$.data.items[*].changeId");
        assertEquals(expected, ids.stream().map(Number::longValue).toList());
        assertTrue(expected.size() >= 2, "시드에 이력이 여러 개 있어야 해요");
    }

    @Test
    void 이력_한_건에는_이전_금액_변경_금액_이유_정한_사람_적용일_만든_시각이_있다() throws Exception {
        long id = insertChange(1, 70000, 73000, "INFLATION", "AI_SUGGESTION", "2030-01-01 09:00:00");

        history(1, "")
                .andExpect(jsonPath("$.data.items[0].changeId").value(id))
                .andExpect(jsonPath("$.data.items[0].prevAmount").value(70000))
                .andExpect(jsonPath("$.data.items[0].amount").value(73000))
                .andExpect(jsonPath("$.data.items[0].reason").value("INFLATION"))
                .andExpect(jsonPath("$.data.items[0].source").value("AI_SUGGESTION"))
                .andExpect(jsonPath("$.data.items[0].effectiveFrom").value("2030-02-01"))
                .andExpect(jsonPath("$.data.items[0].createdAt").value("2030-01-01T09:00:00"));
    }

    @Test
    void 처음_정한_용돈은_이전_금액이_null이다() throws Exception {
        insertChange(1, null, 50000, "INITIAL", "PARENT", "2030-01-01 09:00:00");

        history(1, "")
                .andExpect(jsonPath("$.data.items[0].prevAmount").value(nullValue()))
                .andExpect(jsonPath("$.data.items[0].reason").value("INITIAL"));
    }

    @Test
    void 다시_약속한_횟수는_페이지와_상관없이_전체_개수다() throws Exception {
        int before = jdbc.queryForObject("SELECT COUNT(*) FROM allowance_changes WHERE family_id = 1 AND reason = 'RENEGOTIATION'", Integer.class);
        insertChange(1, 70000, 72000, "RENEGOTIATION", "PARENT", "2030-01-01 09:00:00");
        insertChange(1, 72000, 74000, "RENEGOTIATION", "PARENT", "2030-01-02 09:00:00");
        insertChange(1, 74000, 75000, "ETC", "PARENT", "2030-01-03 09:00:00");

        history(1, "?size=1").andExpect(jsonPath("$.data.renegotiationCount").value(before + 2));
    }

    @Test
    void 페이지를_끝까지_따라가면_빠지거나_겹치는_이력이_없다() throws Exception {
        for (int i = 1; i <= 5; i++) insertChange(1, 70000, 70000 + i * 1000, "ETC", "PARENT", "2030-01-0" + i + " 09:00:00");

        assertEquals(idsInDbOrder(1), walk(1, 2));
        assertEquals(idsInDbOrder(1), walk(1, 1));
        assertEquals(idsInDbOrder(1), walk(1, 100));
    }

    @Test
    void 같은_시각에_만든_이력도_순서가_정해져서_페이지_사이에서_빠지지_않는다() throws Exception {
        for (int i = 0; i < 4; i++) insertChange(1, 70000, 71000 + i, "ETC", "PARENT", "2030-03-01 09:00:00");

        assertEquals(idsInDbOrder(1), walk(1, 1));
    }

    @Test
    void 마지막_페이지는_nextCursor가_null이다() throws Exception {
        history(1, "?size=100").andExpect(jsonPath("$.data.nextCursor").value(nullValue()));
    }

    @Test
    void 용돈을_저장하면_이력_맨_위에_새로_생긴다() throws Exception {
        mockMvc.perform(post("/allowance/changes").header("X-Persona-Id", 1).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":73000,\"reason\":\"INFLATION\",\"allowanceDay\":1,\"autoTransfer\":true,\"settlementDay\":25}"))
                .andExpect(status().isCreated());

        history(1, "")
                .andExpect(jsonPath("$.data.items[0].amount").value(73000))
                .andExpect(jsonPath("$.data.items[0].prevAmount").value(70000))
                .andExpect(jsonPath("$.data.items[0].source").value("PARENT"))
                .andExpect(jsonPath("$.data.items[0].effectiveFrom").value("2026-11-01"));
    }

    @Test
    void 대기_중인_AI_제안은_이력에_들어가지_않는다() throws Exception {
        String body = history(1, "?size=100").andReturn().getResponse().getContentAsString();

        List<Number> amounts = JsonPath.read(body, "$.data.items[*].amount");
        assertFalse(amounts.contains(73000), "시드의 11월 73,000원은 아직 제안일 뿐이라 이력에 없어야 해요");
    }

    @Test
    void 다른_가족의_이력은_섞이지_않는다() throws Exception {
        long other = insertChange(2, 60000, 61000, "ETC", "PARENT", "2030-01-01 09:00:00");

        String bodyA = history(1, "?size=100").andReturn().getResponse().getContentAsString();
        String bodyB = history(3, "?size=100").andReturn().getResponse().getContentAsString();

        List<Number> idsA = JsonPath.read(bodyA, "$.data.items[*].changeId");
        List<Number> idsB = JsonPath.read(bodyB, "$.data.items[*].changeId");
        assertFalse(idsA.stream().anyMatch(n -> n.longValue() == other), "다른 가족의 이력이 섞였어요");
        assertEquals(idsInDbOrder(2), idsB.stream().map(Number::longValue).toList());
    }

    @Test
    void size가_범위를_벗어나면_400() throws Exception {
        history(1, "?size=0").andExpect(status().isBadRequest());
        history(1, "?size=101").andExpect(status().isBadRequest());
        history(1, "?size=-1").andExpect(status().isBadRequest());
        history(1, "?size=abc").andExpect(status().isBadRequest());
    }

    @Test
    void 깨진_커서는_400() throws Exception {
        history(1, "?cursor=%%%").andExpect(status().isBadRequest());
        history(1, "?cursor=" + cursorOf("not-a-time|3")).andExpect(status().isBadRequest());
        history(1, "?cursor=" + cursorOf("2030-01-01T09:00:00")).andExpect(status().isBadRequest()); // ID 없음
        history(1, "?cursor=" + cursorOf("2030-01-01T09:00:00|abc")).andExpect(status().isBadRequest());
    }

    @Test
    void DB가_받지_못하는_연도의_커서는_500이_아니라_400() throws Exception {
        history(1, "?cursor=" + cursorOf("+10000-01-01T00:00:00|1")).andExpect(status().isBadRequest());
    }

    @Test
    void 자녀가_조회하면_403() throws Exception {
        history(2, "")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("부모만 할 수 있어요"));
    }

    @Test
    void 없는_페르소나는_404_헤더가_없으면_400() throws Exception {
        history(999, "").andExpect(status().isNotFound());
        mockMvc.perform(get("/allowance/changes")).andExpect(status().isBadRequest());
    }
}
