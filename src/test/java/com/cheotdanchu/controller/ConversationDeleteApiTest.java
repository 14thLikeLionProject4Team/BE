package com.cheotdanchu.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 테스트마다 롤백 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ConversationDeleteApiTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbc;

    private long insertMessage(long memberId, String content) {
        jdbc.update("INSERT INTO chat_messages (member_id, sender, content, created_at) VALUES (?, 'CHILD', ?, '2026-10-19 15:00:00')", memberId, content);
        return jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    @Test
    void 내_대화만_지우고_전할_말은_출처_연결만_끊는다() throws Exception {
        long source = insertMessage(2, "엄마한테 선물비 더 달라고 하고 싶어");
        insertMessage(2, "고마워");
        insertMessage(4, "뽀로로의 말");
        jdbc.update("INSERT INTO child_messages (family_id, message_id, request, status) VALUES (1, ?, '친구 생일 선물비가 더 필요해요', 'CANDIDATE')", source);

        mockMvc.perform(delete("/conversations").header("X-Persona-Id", 2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("대화 기록을 지웠어요."))
                .andExpect(jsonPath("$.data.deletedCount").value(2));

        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM chat_messages WHERE member_id = 2", Integer.class));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM chat_messages WHERE member_id = 4", Integer.class));
        Map<String, Object> kept = jdbc.queryForMap("SELECT message_id, status FROM child_messages WHERE family_id = 1");
        assertNull(kept.get("message_id"));
        assertEquals("CANDIDATE", kept.get("status"));
    }

    @Test
    void 지울_대화가_없어도_성공() throws Exception {
        mockMvc.perform(delete("/conversations").header("X-Persona-Id", 2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.deletedCount").value(0));
    }

    @Test
    void 부모가_호출하면_403() throws Exception {
        mockMvc.perform(delete("/conversations").header("X-Persona-Id", 1))
                .andExpect(status().isForbidden());
    }
}
