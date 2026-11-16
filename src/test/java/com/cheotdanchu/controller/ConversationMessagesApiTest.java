package com.cheotdanchu.controller;

import com.jayway.jsonpath.JsonPath;
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

/** 대화 시드는 없어서 테스트에서 직접 넣는다. 테스트마다 롤백 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ConversationMessagesApiTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbc;

    private void insert(long memberId, String sender, String cardType, String content, String payload) {
        jdbc.update("INSERT INTO chat_messages (member_id, sender, content, card_type, payload, created_at) VALUES (?, ?, ?, ?, ?, '2026-10-19 15:14:40')",
                memberId, sender, content, cardType, payload);
    }

    @Test
    void 내_대화만_최신순으로_카드_데이터와_함께_본다() throws Exception {
        insert(2, "CHILD", "TEXT", "청구 예정액이 뭐야?", null);
        insert(2, "BOT", "RAG", "청구 예정액은 결제일에 빠져나갈 돈이에요.",
                "{\"inScope\": true, \"sources\": [{\"title\": \"청구 예정액\", \"source\": \"첫단추 서비스 안내\"}]}");
        insert(4, "CHILD", "TEXT", "뽀로로의 말", null);

        mockMvc.perform(get("/conversations/messages").header("X-Persona-Id", 2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("대화 기록 조회 성공"))
                .andExpect(jsonPath("$.data.messages.length()").value(2))
                .andExpect(jsonPath("$.data.messages[0].sender").value("BOT"))
                .andExpect(jsonPath("$.data.messages[0].cardType").value("RAG"))
                .andExpect(jsonPath("$.data.messages[0].payload.sources[0].title").value("청구 예정액"))
                .andExpect(jsonPath("$.data.messages[1].sender").value("CHILD"))
                .andExpect(jsonPath("$.data.messages[1].payload").value(nullValue()))
                .andExpect(jsonPath("$.data.nextCursor").value(nullValue()));
    }

    @Test
    void 대화는_20개씩_커서로_넘긴다() throws Exception {
        for (int i = 0; i < 23; i++) insert(2, "CHILD", "TEXT", "메시지 " + i, null);

        String body = mockMvc.perform(get("/conversations/messages").header("X-Persona-Id", 2))
                .andExpect(jsonPath("$.data.messages.length()").value(20))
                .andExpect(jsonPath("$.data.messages[0].content").value("메시지 22"))
                .andReturn().getResponse().getContentAsString();
        Number cursor = JsonPath.read(body, "$.data.nextCursor");

        mockMvc.perform(get("/conversations/messages").param("cursor", cursor.toString()).header("X-Persona-Id", 2))
                .andExpect(jsonPath("$.data.messages.length()").value(3))
                .andExpect(jsonPath("$.data.nextCursor").value(nullValue()));
    }

    @Test
    void 부모가_호출하면_403() throws Exception {
        mockMvc.perform(get("/conversations/messages").header("X-Persona-Id", 1))
                .andExpect(status().isForbidden());
    }
}
