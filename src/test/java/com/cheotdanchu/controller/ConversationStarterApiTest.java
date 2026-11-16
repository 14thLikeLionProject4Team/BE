package com.cheotdanchu.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 데모 기준일 10/19, 가족 A 이번 정산 결제일 10/25. 테스트마다 롤백 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ConversationStarterApiTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void 첫_인사에_결제일까지_남은_날을_넣는다() throws Exception {
        mockMvc.perform(get("/conversations/starter").header("X-Persona-Id", 2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("대화 시작 정보 조회 성공"))
                .andExpect(jsonPath("$.data.greeting").value("루피야 안녕! 이번 달 결제일까지 6일 남았어."))
                .andExpect(jsonPath("$.data.privacyNotice").value("부모님은 이 대화를 볼 수 없어요. 네가 허락한 말만 전해져."))
                .andExpect(jsonPath("$.data.chips.length()").value(3));
    }

    @Test
    void 진행_중인_정산이_없으면_결제일_없이_인사한다() throws Exception {
        jdbc.update("UPDATE settlements SET period_end = '2026-10-01' WHERE family_id = 1 AND period_end >= '2026-10-19'");

        mockMvc.perform(get("/conversations/starter").header("X-Persona-Id", 2))
                .andExpect(jsonPath("$.data.greeting").value("루피야 안녕! 궁금한 걸 편하게 물어봐."));
    }

    @Test
    void 부모가_호출하면_403() throws Exception {
        mockMvc.perform(get("/conversations/starter").header("X-Persona-Id", 1))
                .andExpect(status().isForbidden());
    }
}
