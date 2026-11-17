package com.cheotdanchu.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 시연 기준일 2026-10-19. 가족 A 루피(id 2, 2007-11-02생, 만 18세)와 루피 엄마(id 1),
 * 가족 B 뽀로로(id 4, 2007-10-03생, 이미 만 19세)와 뽀로로 엄마(id 3). 테스트마다 롤백된다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class MyInfoApiTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbc;

    private ResultActions childMy(int personaId) throws Exception {
        return mockMvc.perform(get("/child/my").header("X-Persona-Id", personaId));
    }

    private ResultActions parentMy(int personaId) throws Exception {
        return mockMvc.perform(get("/parent/my").header("X-Persona-Id", personaId));
    }

    @Test
    void 루피의_내_정보는_명세_예시와_같다() throws Exception {
        childMy(2)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("조회 성공"))
                .andExpect(jsonPath("$.data.memberId").value(2))
                .andExpect(jsonPath("$.data.name").value("루피"))
                .andExpect(jsonPath("$.data.birthDate").value("2007-11-02"))
                .andExpect(jsonPath("$.data.age").value(18))
                .andExpect(jsonPath("$.data.familyCardLinked").value(true))
                .andExpect(jsonPath("$.data.visibility").value("TOTAL_ONLY"))
                .andExpect(jsonPath("$.data.adultTransition.transitioned").value(false))
                .andExpect(jsonPath("$.data.adultTransition.at").value(nullValue()));
    }

    @Test
    void 이미_만_19세가_된_뽀로로는_성년_전환이_true이고_전환일이_나온다() throws Exception {
        childMy(4)
                .andExpect(jsonPath("$.data.name").value("뽀로로"))
                .andExpect(jsonPath("$.data.age").value(19))
                .andExpect(jsonPath("$.data.adultTransition.transitioned").value(true))
                .andExpect(jsonPath("$.data.adultTransition.at").value("2026-10-03")); // 생일 + 19년
    }

    @Test
    void 만_19세_생일_당일부터_성년_전환이다() throws Exception {
        jdbc.update("UPDATE members SET birth_date = '2007-10-19' WHERE member_id = 2"); // 오늘이 생일

        childMy(2)
                .andExpect(jsonPath("$.data.age").value(19))
                .andExpect(jsonPath("$.data.adultTransition.transitioned").value(true))
                .andExpect(jsonPath("$.data.adultTransition.at").value("2026-10-19"));
    }

    @Test
    void 생일_하루_전까지는_만_18세이고_성년_전환이_아니다() throws Exception {
        jdbc.update("UPDATE members SET birth_date = '2007-10-20' WHERE member_id = 2"); // 내일이 생일

        childMy(2)
                .andExpect(jsonPath("$.data.age").value(18))
                .andExpect(jsonPath("$.data.adultTransition.transitioned").value(false));
    }

    @Test
    void 부모가_자녀_내_정보를_조회하면_403() throws Exception {
        childMy(1)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("자녀만 조회할 수 있어요"));
    }

    @Test
    void 루피_엄마의_정보는_보호자_이름과_아이_선택_목록을_준다() throws Exception {
        parentMy(1)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("조회 성공"))
                .andExpect(jsonPath("$.data.memberId").value(1))
                .andExpect(jsonPath("$.data.name").value("루피 엄마"))
                .andExpect(jsonPath("$.data.relation").value("루피 보호자"))
                .andExpect(jsonPath("$.data.cardHolder").value(true))
                .andExpect(jsonPath("$.data.children.length()").value(1))
                .andExpect(jsonPath("$.data.children[0].memberId").value(2))
                .andExpect(jsonPath("$.data.children[0].name").value("루피"));
    }

    @Test
    void 아이가_성년이_된_뽀로로_엄마도_아이_선택_목록은_그대로다() throws Exception {
        parentMy(3)
                .andExpect(jsonPath("$.data.name").value("뽀로로 엄마"))
                .andExpect(jsonPath("$.data.relation").value("뽀로로 보호자"))
                .andExpect(jsonPath("$.data.children[0].memberId").value(4));
    }

    @Test
    void 아이가_없는_부모는_관계가_null이고_아이_선택_목록이_비어_있다() throws Exception {
        jdbc.update("INSERT INTO families (name) VALUES ('아이 없는 가족')");
        long familyId = jdbc.queryForObject("SELECT MAX(family_id) FROM families", Long.class);
        jdbc.update("INSERT INTO members (family_id, role, name, birth_date) VALUES (?, 'PARENT', '혼자 부모', '1980-01-01')", familyId);
        long parentId = jdbc.queryForObject("SELECT MAX(member_id) FROM members", Long.class);

        parentMy((int) parentId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("혼자 부모"))
                .andExpect(jsonPath("$.data.relation").value(nullValue()))
                .andExpect(jsonPath("$.data.children.length()").value(0));
    }

    @Test
    void 자녀가_부모_정보를_조회하면_403() throws Exception {
        parentMy(2)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("부모만 할 수 있어요"));
    }

    @Test
    void 없는_페르소나는_404_헤더가_없으면_400() throws Exception {
        childMy(999).andExpect(status().isNotFound());
        parentMy(999).andExpect(status().isNotFound());
        mockMvc.perform(get("/child/my")).andExpect(status().isBadRequest());
    }
}
