package com.cheotdanchu.controller;

import com.cheotdanchu.ai.GroqClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** LLM은 가짜로 바꿔서 답을 정해 둔다. 루피(2) 자녀, 루피 엄마(1) 부모. 테스트마다 롤백 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ConversationSendApiTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbc;

    @MockitoBean
    GroqClient llm;

    private ResultActions send(int personaId, String content) throws Exception {
        return mockMvc.perform(post("/conversations/messages").header("X-Persona-Id", personaId)
                .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"" + content + "\"}"));
    }

    @Test
    void 금융_질문은_자료_출처와_함께_답하고_대화를_저장한다() throws Exception {
        when(llm.chatJson(anyString(), anyString())).thenReturn(
                Map.of("kind", "금융지식"),
                Map.of("answer", "청구 예정액은 결제일에 빠져나갈 돈을 미리 모아 본 금액이에요.", "used_ids", java.util.List.of("svc_bill_expected")));

        send(2, "청구 예정액이 뭐야?")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("메시지 전송 성공"))
                .andExpect(jsonPath("$.data.reply.sender").value("BOT"))
                .andExpect(jsonPath("$.data.reply.cardType").value("RAG"))
                .andExpect(jsonPath("$.data.reply.content").value("청구 예정액은 결제일에 빠져나갈 돈을 미리 모아 본 금액이에요."))
                .andExpect(jsonPath("$.data.reply.payload.inScope").value(true))
                .andExpect(jsonPath("$.data.reply.payload.sources[0].title").value("청구 예정액"))
                .andExpect(jsonPath("$.data.reply.payload.sources[0].source").value("첫단추 서비스 안내"))
                .andExpect(jsonPath("$.data.childMessage").value(nullValue()));

        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM chat_messages WHERE member_id = 2", Integer.class));
    }

    @Test
    void 고민_대화에서_부모님께_바라는_게_나오면_전할_말_후보를_만든다() throws Exception {
        when(llm.chatJson(anyString(), anyString())).thenReturn(Map.of("kind", "고민"));
        when(llm.chatJson(anyString(), anyList(), anyString())).thenReturn(Map.of(
                "reply", "친구 생일을 잘 챙기고 싶은 마음이 느껴져요.",
                "danger", false,
                "to_parent", Map.of("request", "다음 달 친구 생일 선물비가 더 필요해요", "reason", "미리 말해두고 싶어요")));

        send(2, "다음 달에 친구 생일이라 엄마한테 선물비 더 달라고 하고 싶어")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.reply.cardType").value("TEXT"))
                .andExpect(jsonPath("$.data.childMessage.request").value("다음 달 친구 생일 선물비가 더 필요해요"))
                .andExpect(jsonPath("$.data.childMessage.reason").value("미리 말해두고 싶어요"))
                .andExpect(jsonPath("$.data.childMessage.status").value("CANDIDATE"));

        Map<String, Object> saved = jdbc.queryForMap(
                "SELECT cm.status, m.sender FROM child_messages cm JOIN chat_messages m ON m.message_id = cm.message_id WHERE cm.family_id = 1");
        assertEquals("CANDIDATE", saved.get("status"));
        assertEquals("CHILD", saved.get("sender"));
    }

    @Test
    void LLM이_쓴_전할_말이_200자를_넘으면_잘라서_저장한다() throws Exception {
        String longRequest = "선".repeat(250);
        when(llm.chatJson(anyString(), anyString())).thenReturn(Map.of("kind", "고민"));
        when(llm.chatJson(anyString(), anyList(), anyString())).thenReturn(Map.of(
                "reply", "마음이 느껴져요.", "danger", false, "to_parent", Map.of("request", longRequest)));

        send(2, "엄마한테 부탁하고 싶은 게 있어")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.childMessage.request").value("선".repeat(200)))
                .andExpect(jsonPath("$.data.childMessage.reason").value(nullValue()));
    }

    @Test
    void 메시지가_500자를_넘으면_400() throws Exception {
        send(2, "가".repeat(501))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("메시지는 500자까지 보낼 수 있어요."));
    }

    @Test
    void 빈_메시지는_400() throws Exception {
        send(2, "  ")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("메시지를 입력해 주세요."));
    }

    @Test
    void 부모가_보내면_403() throws Exception {
        send(1, "안녕").andExpect(status().isForbidden());
    }

    @Test
    void LLM이_답하지_않으면_503() throws Exception {
        when(llm.chatJson(anyString(), anyString())).thenReturn(null);

        send(2, "청구 예정액이 뭐야?")
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value("지금은 답변하기 어려워요. 잠시 후 다시 물어봐 주세요."));
    }

    @Test
    void 기존_api_ai_chat은_LLM이_답하지_않아도_200으로_금융지식_처리한다() throws Exception {
        when(llm.chatJson(anyString(), anyString())).thenReturn(null);

        mockMvc.perform(post("/api/ai/chat").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"personaId\":\"2\",\"childName\":\"루피\",\"message\":\"청구 예정액이 뭐야?\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kind").value("금융지식"));
    }
}
