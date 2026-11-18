package com.cheotdanchu.controller;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 시드 기준: 가족 A 루피(id 2)의 계좌 잔액은 132,900원(이번 달 쓸 수 있는 돈 73,000 + 지난달까지 남은 돈 59,900),
 * 루피 엄마(id 1)의 계좌 잔액은 0원. 가족 B 뽀로로(id 4), 뽀로로 엄마(id 3). 테스트마다 롤백된다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AccountApiTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbc;

    private ResultActions accounts(int personaId) throws Exception {
        return mockMvc.perform(get("/accounts").header("X-Persona-Id", personaId));
    }

    @Test
    void 자녀는_자기_잔액만_보고_부모_계좌의_잔액은_null이다() throws Exception {
        accounts(2)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("조회 성공"))
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.items[0].ownerRole").value("CHILD"))
                .andExpect(jsonPath("$.data.items[0].maskedAccountNumber").value("****-**-4567"))
                .andExpect(jsonPath("$.data.items[0].balance").value(132900))
                .andExpect(jsonPath("$.data.items[1].ownerRole").value("PARENT"))
                .andExpect(jsonPath("$.data.items[1].maskedAccountNumber").value("****-**-1234"))
                .andExpect(jsonPath("$.data.items[1].balance").value(nullValue()));
    }

    @Test
    void 부모도_자녀_계좌가_먼저_나오고_자기_잔액만_본다() throws Exception {
        jdbc.update("UPDATE accounts SET balance = 500000 WHERE member_id = 1");

        accounts(1)
                .andExpect(jsonPath("$.data.items[0].ownerRole").value("CHILD"))
                .andExpect(jsonPath("$.data.items[0].balance").value(nullValue()))
                .andExpect(jsonPath("$.data.items[1].ownerRole").value("PARENT"))
                .andExpect(jsonPath("$.data.items[1].balance").value(500000));
    }

    @Test
    void 은행명과_계좌_ID가_계좌_테이블과_같다() throws Exception {
        String body = accounts(2).andReturn().getResponse().getContentAsString();

        long childAccountId = jdbc.queryForObject("SELECT account_id FROM accounts WHERE member_id = 2", Long.class);
        String bank = jdbc.queryForObject("SELECT bank_name FROM accounts WHERE member_id = 2", String.class);
        assertEquals(childAccountId, ((Number) JsonPath.read(body, "$.data.items[0].accountId")).longValue());
        assertEquals(bank, JsonPath.read(body, "$.data.items[0].bankName"));
    }

    @Test
    void 실제_계좌번호는_응답에_나오지_않는다() throws Exception {
        String body = accounts(2).andReturn().getResponse().getContentAsString();

        assertFalse(body.contains("3333-01-4567"), body);
        assertFalse(body.contains("3333-01-1234"), body);
    }

    @Test
    void 잔액은_이번_달_쓸_수_있는_돈과_지난달까지_남은_돈의_합이다() throws Exception {
        String current = mockMvc.perform(get("/settlements/current").header("X-Persona-Id", 2)).andReturn().getResponse().getContentAsString();
        long available = ((Number) JsonPath.read(current, "$.data.availableAmount")).longValue();
        long carryover = ((Number) JsonPath.read(current, "$.data.carryoverAmount")).longValue();

        accounts(2).andExpect(jsonPath("$.data.items[0].balance").value(available + carryover));
    }

    @Test
    void 가족B는_자기_가족의_계좌만_본다() throws Exception {
        accounts(4)
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.items[0].maskedAccountNumber").value("****-**-7788"))
                .andExpect(jsonPath("$.data.items[1].maskedAccountNumber").value("****-**-5566"));
    }

    @Test
    void 자녀가_만_19세가_되면_부모에게는_자녀_계좌가_빠지고_부모_계좌만_나온다() throws Exception {
        accounts(3) // 뽀로로 엄마: 뽀로로는 이미 만 19세
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].ownerRole").value("PARENT"))
                .andExpect(jsonPath("$.data.items[0].maskedAccountNumber").value("****-**-5566"));
    }

    @Test
    void 만_19세가_된_자녀_본인은_두_계좌를_그대로_본다() throws Exception {
        accounts(4)
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.items[0].ownerRole").value("CHILD"));
    }

    @Test
    void 자녀가_성년이_되기_전에는_부모도_자녀_계좌를_본다() throws Exception {
        accounts(1).andExpect(jsonPath("$.data.items.length()").value(2)); // 루피는 11/2에 만 19세
    }

    @Test
    void 계좌가_없는_구성원은_목록에서_빠진다() throws Exception {
        jdbc.update("DELETE FROM transfers WHERE from_account_id = (SELECT account_id FROM accounts WHERE member_id = 1) "
                + "OR to_account_id = (SELECT account_id FROM accounts WHERE member_id = 1)");
        jdbc.update("DELETE FROM accounts WHERE member_id = 1");

        accounts(2)
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].ownerRole").value("CHILD"));
    }

    @Test
    void 없는_페르소나는_404() throws Exception {
        accounts(999).andExpect(status().isNotFound());
    }

    @Test
    void 헤더가_없으면_400() throws Exception {
        mockMvc.perform(get("/accounts")).andExpect(status().isBadRequest());
    }
}
