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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 시드 기준: 가족 A(루피 엄마 id 1, 루피 id 2)는 용돈 70,000원, 다음 달 1일(2026-11-01)부터 적용될 용돈도 70,000원. 테스트마다 롤백 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AllowanceChangePreviewApiTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbc;

    private ResultActions preview(int personaId, String amount, String reason) throws Exception {
        return preview(personaId, amount, reason, null);
    }

    private ResultActions preview(int personaId, String amount, String reason, Long suggestionId) throws Exception {
        MockHttpServletRequestBuilder request = get("/allowance/changes/preview").header("X-Persona-Id", personaId);
        if (amount != null) request.param("amount", amount);
        if (reason != null) request.param("reason", reason);
        if (suggestionId != null) request.param("suggestionId", suggestionId.toString());
        return mockMvc.perform(request);
    }

    /** 용돈 변경 저장 요청 (보내는 날·자동이체·결제일은 시드 값 그대로) */
    private ResultActions save(int amount, String reason) throws Exception {
        String reasonField = reason == null ? "" : ",\"reason\":\"" + reason + "\"";
        return mockMvc.perform(post("/allowance/changes").header("X-Persona-Id", 1).contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\":" + amount + reasonField + ",\"allowanceDay\":1,\"autoTransfer\":true,\"settlementDay\":25}"));
    }

    @Test
    void 물가로_올리면_알림_문구와_차이와_적용일을_미리_본다() throws Exception {
        preview(1, "73000", "INFLATION")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("조회 성공"))
                .andExpect(jsonPath("$.data.message").value("11월부터 용돈이 73,000원이 돼요. 물가가 올라서 부모님이 올렸어요."))
                .andExpect(jsonPath("$.data.diff").value(3000))
                .andExpect(jsonPath("$.data.effectiveFrom").value("2026-11-01"));
    }

    @Test
    void 이유가_없거나_물가가_아니면_이유_문구가_없고_줄이면_차이가_음수다() throws Exception {
        preview(1, "68000", null)
                .andExpect(jsonPath("$.data.message").value("11월부터 용돈이 68,000원이 돼요."))
                .andExpect(jsonPath("$.data.diff").value(-2000));
        preview(1, "72000", "SCHOOL")
                .andExpect(jsonPath("$.data.message").value("11월부터 용돈이 72,000원이 돼요."));
    }

    @Test
    void 물가를_골라도_금액이_오르지_않으면_올렸다는_문구가_없고_저장_알림도_같다() throws Exception {
        preview(1, "68000", "INFLATION")
                .andExpect(jsonPath("$.data.message").value("11월부터 용돈이 68,000원이 돼요."))
                .andExpect(jsonPath("$.data.diff").value(-2000));

        save(68000, "INFLATION").andExpect(status().isCreated());
        assertEquals("11월부터 용돈이 68,000원이 돼요.", jdbc.queryForObject(
                "SELECT body FROM notifications WHERE member_id = 2 AND type = 'ALLOWANCE_CHANGED'", String.class));
    }

    @Test
    void 금액이_이미_정해진_값과_같으면_저장해도_알림이_없어서_문구는_null이다() throws Exception {
        preview(1, "70000", "INFLATION")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.message").value(nullValue()))
                .andExpect(jsonPath("$.data.diff").value(0))
                .andExpect(jsonPath("$.data.effectiveFrom").value("2026-11-01"));
    }

    // 제안을 따라 저장하면 이유를 안 보내도 물가로 남는다. 미리보기도 제안 ID 를 받으면 같은 문구를 보여준다
    @Test
    void AI_제안_그대로의_금액이면_이유를_안_보내도_물가_문구가_나오고_저장_알림과_같다() throws Exception {
        String shown = JsonPath.read(
                preview(1, "73000", null, 1L).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "$.data.message");
        assertEquals("11월부터 용돈이 73,000원이 돼요. 물가가 올라서 부모님이 올렸어요.", shown);

        mockMvc.perform(post("/allowance/changes").header("X-Persona-Id", 1).contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\":73000,\"allowanceDay\":1,\"autoTransfer\":true,\"settlementDay\":25,\"suggestionId\":1}"))
                .andExpect(status().isCreated());

        assertEquals(shown, jdbc.queryForObject(
                "SELECT body FROM notifications WHERE member_id = 2 AND type = 'ALLOWANCE_CHANGED'", String.class));
    }

    @Test
    void 제안과_다른_금액이면_제안_ID가_있어도_물가_문구가_없다() throws Exception {
        preview(1, "72000", null, 1L)
                .andExpect(jsonPath("$.data.message").value("11월부터 용돈이 72,000원이 돼요."));
    }

    @Test
    void 제안_ID가_있어도_직접_고른_이유가_우선한다() throws Exception {
        preview(1, "73000", "SCHOOL", 1L)
                .andExpect(jsonPath("$.data.message").value("11월부터 용돈이 73,000원이 돼요."));
    }

    @Test
    void 이미_처리했거나_다른_가족이거나_없는_제안이면_저장과_같은_오류다() throws Exception {
        preview(3, "73000", null, 1L).andExpect(status().isNotFound());
        preview(1, "73000", null, 999L).andExpect(status().isNotFound());

        mockMvc.perform(patch("/allowance/suggestions/1").header("X-Persona-Id", 1)
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"KEPT\"}")).andExpect(status().isOk());
        preview(1, "73000", null, 1L).andExpect(status().isConflict());
    }

    @Test
    void 미리보기는_저장하지_않는다() throws Exception {
        preview(1, "73000", "INFLATION").andExpect(status().isOk());

        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM allowance_changes WHERE family_id = 1", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM notifications WHERE member_id = 2", Integer.class));
    }

    @Test
    void 미리본_문구는_저장했을_때_아이가_받는_알림_문구와_같다() throws Exception {
        String shown = JsonPath.read(
                preview(1, "74000", "INFLATION").andReturn().getResponse().getContentAsString(), "$.data.message");

        save(74000, "INFLATION").andExpect(status().isCreated());

        assertEquals(shown, jdbc.queryForObject(
                "SELECT body FROM notifications WHERE member_id = 2 AND type = 'ALLOWANCE_CHANGED'", String.class));
    }

    @Test
    void 다음_달부터_적용될_용돈이_이미_정해져_있으면_그것과의_차이를_준다() throws Exception {
        save(72000, null).andExpect(status().isCreated());

        preview(1, "74000", null).andExpect(jsonPath("$.data.diff").value(2000));
    }

    @Test
    void 용돈을_정한_적이_없으면_차이는_null이다() throws Exception {
        jdbc.update("DELETE FROM allowance_changes WHERE family_id = 1");

        preview(1, "70000", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.message").value("11월부터 용돈이 70,000원이 돼요."))
                .andExpect(jsonPath("$.data.diff").value(nullValue()));
    }

    @Test
    void 금액이_없거나_1원_미만이거나_이유가_잘못되면_400() throws Exception {
        preview(1, null, null).andExpect(status().isBadRequest());
        preview(1, "0", null).andExpect(status().isBadRequest());
        preview(1, "-1000", null).andExpect(status().isBadRequest());
        preview(1, "abc", null).andExpect(status().isBadRequest());
        preview(1, "73000", "INITIAL").andExpect(status().isBadRequest());
        preview(1, "73000", "MAYBE").andExpect(status().isBadRequest());
    }

    @Test
    void 자녀가_조회하면_403() throws Exception {
        preview(2, "73000", null)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("부모만 할 수 있어요"));
    }
}
