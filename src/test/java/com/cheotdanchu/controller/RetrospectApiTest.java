package com.cheotdanchu.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
}
