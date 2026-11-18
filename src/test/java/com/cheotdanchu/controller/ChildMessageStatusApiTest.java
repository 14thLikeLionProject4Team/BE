package com.cheotdanchu.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 루피(2) 자녀, 루피 엄마(1) 부모, 뽀로로 엄마(3)는 다른 가족. 데모 기준일 10/19. 테스트마다 롤백 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ChildMessageStatusApiTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbc;

    private long insert(String status) {
        jdbc.update("INSERT INTO child_messages (family_id, request, reason, status) VALUES (1, '용돈을 조금 더 받고 싶어요', '친구 생일이 있어요', ?)", status);
        return jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    private ResultActions change(int personaId, long id, String status) throws Exception {
        return mockMvc.perform(patch("/child-messages/" + id).header("X-Persona-Id", personaId)
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"" + status + "\"}"));
    }

    @Test
    void 자녀가_지금_전하면_SENT가_되고_부모_결정_카드에_뜬다() throws Exception {
        long id = insert("CANDIDATE");

        change(2, id, "SENT")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("부모님께 전했어요."))
                .andExpect(jsonPath("$.data.childMessageId").value(id))
                .andExpect(jsonPath("$.data.status").value("SENT"))
                .andExpect(jsonPath("$.data.delivery").value("NOW"))
                .andExpect(jsonPath("$.data.sentAt").value(org.hamcrest.Matchers.startsWith("2026-10-19T")));

        mockMvc.perform(get("/parent/decisions").header("X-Persona-Id", 1))
                .andExpect(jsonPath("$.data.cards[?(@.refId == " + id + ")].type").value("CHILD_MESSAGE"));
    }

    @Test
    void 자녀는_월말에_전하기나_버리기를_고를_수_있다() throws Exception {
        change(2, insert("CANDIDATE"), "MONTHLY")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("MONTHLY"))
                .andExpect(jsonPath("$.data.delivery").value(nullValue()));
        change(2, insert("CANDIDATE"), "DISCARDED")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("저장하지 않았어요."));
    }

    @Test
    void 부모가_답하면_결정_카드에서_사라진다() throws Exception {
        long id = insert("SENT");

        change(1, id, "ACKNOWLEDGED")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("확인했어요."))
                .andExpect(jsonPath("$.data.status").value("ACKNOWLEDGED"))
                .andExpect(jsonPath("$.data.decidedAt").value(org.hamcrest.Matchers.startsWith("2026-10-19T")));

        mockMvc.perform(get("/parent/decisions").header("X-Persona-Id", 1))
                .andExpect(jsonPath("$.data.cards[?(@.refId == " + id + " && @.type == 'CHILD_MESSAGE')]").isEmpty());
    }

    @Test
    void 지금_상태나_역할에_맞지_않는_값은_409() throws Exception {
        long candidate = insert("CANDIDATE");
        long sent = insert("SENT");

        change(1, candidate, "ACKNOWLEDGED").andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("지금은 이 상태로 바꿀 수 없어요."));
        change(1, sent, "SENT").andExpect(status().isConflict());
        change(2, sent, "DISCARDED").andExpect(status().isConflict());
        change(2, candidate, "ACKNOWLEDGED").andExpect(status().isConflict());
        change(2, candidate, "CANDIDATE").andExpect(status().isConflict());
    }

    @Test
    void 다른_가족의_말은_404() throws Exception {
        change(3, insert("SENT"), "ACKNOWLEDGED").andExpect(status().isNotFound());
    }

    @Test
    void 없는_상태값은_400() throws Exception {
        change(2, insert("CANDIDATE"), "MAYBE").andExpect(status().isBadRequest());
    }
}
