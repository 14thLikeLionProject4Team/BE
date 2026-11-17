package com.cheotdanchu.controller;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import jakarta.persistence.EntityManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 시연 기준일 2026-10-19. 가족 B 뽀로로(id 4)는 바로 발급할 수 있고(발급한 확인서는 90일 유효, 2027-01-17까지),
 * 가족 A 루피(id 2)는 확인서를 JDBC 로 직접 넣어서 쓴다. 테스트마다 롤백된다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CertificateShareApiTest {

    private static final String BASE_URL = "https://example.com/certificates/shared/";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    EntityManager em;

    private ResultActions share(int personaId, long certificateId, String body) throws Exception {
        return mockMvc.perform(post("/child/certificates/" + certificateId + "/share").header("X-Persona-Id", personaId)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions share(int personaId, long certificateId, int hours) throws Exception {
        return share(personaId, certificateId, "{\"expiresInHours\":" + hours + "}");
    }

    private long issueForBororo() throws Exception {
        String body = mockMvc.perform(post("/child/certificates").header("X-Persona-Id", 4)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"identityVerified\":true}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.data.certificateId")).longValue();
    }

    private long insertCertificate(int memberId, String expiresAt) {
        jdbc.update("INSERT INTO certificates (member_id, full_paid_months, total_months, max_streak, avg_margin_rate, "
                + "self_resolved_count, shortfall_count, file_path, issued_at, expires_at) "
                + "VALUES (?, 12, 12, 12, 7.5, 0, 1, 'x.pdf', '2026-07-01 10:00:00', ?)", memberId, expiresAt);
        return jdbc.queryForObject("SELECT MAX(certificate_id) FROM certificates", Long.class);
    }

    private String savedToken(long certificateId) {
        em.flush(); // 확인서 수정은 영속성 컨텍스트에 있어서 JDBC 로 읽기 전에 DB 로 내보낸다
        return jdbc.queryForObject("SELECT share_token FROM certificates WHERE certificate_id = ?", String.class, certificateId);
    }

    @Test
    void 기한이_있는_공유_링크를_만든다() throws Exception {
        long id = issueForBororo();

        String body = share(4, id, 72)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("공유 링크를 만들었어요"))
                .andExpect(jsonPath("$.data.shareUrl").value(matchesPattern(BASE_URL + "[0-9a-f]{32}")))
                .andExpect(jsonPath("$.data.shareExpiresAt").value(startsWith("2026-10-22T"))) // 시연 기준일 + 72시간
                .andReturn().getResponse().getContentAsString();

        String url = JsonPath.read(body, "$.data.shareUrl");
        assertEquals(url.substring(BASE_URL.length()), savedToken(id)); // 링크의 토큰이 DB 에 저장된다
        em.flush();
        assertEquals("2026-10-22", jdbc.queryForObject(
                "SELECT DATE(share_expires_at) FROM certificates WHERE certificate_id = ?", String.class, id));
    }

    @Test
    void 다시_만들면_이전_링크는_못_쓴다() throws Exception {
        long id = issueForBororo();
        String first = JsonPath.read(share(4, id, 24).andReturn().getResponse().getContentAsString(), "$.data.shareUrl");
        String firstToken = savedToken(id);

        String second = JsonPath.read(share(4, id, 24).andReturn().getResponse().getContentAsString(), "$.data.shareUrl");

        assertNotEquals(first, second);
        assertNotEquals(firstToken, savedToken(id)); // 확인서마다 링크는 하나라서 새 토큰으로 바뀐다
        assertEquals(second.substring(BASE_URL.length()), savedToken(id));
    }

    @Test
    void 유효_시간은_1부터_168시간까지다() throws Exception {
        long id = issueForBororo();

        share(4, id, 1).andExpect(status().isCreated());
        share(4, id, 168).andExpect(status().isCreated());
        share(4, id, 0).andExpect(status().isBadRequest());
        share(4, id, 169).andExpect(status().isBadRequest());
        share(4, id, -5).andExpect(status().isBadRequest());
    }

    @Test
    void 유효_시간이_없으면_400() throws Exception {
        long id = issueForBororo();

        share(4, id, "{}").andExpect(status().isBadRequest());
        share(4, id, "{\"expiresInHours\":\"abc\"}").andExpect(status().isBadRequest());
    }

    @Test
    void 확인서_유효기한을_넘기는_링크는_만들지_않는다() throws Exception {
        long id = insertCertificate(2, "2026-10-21 10:00:00"); // 기준일 10-19 에서 이틀 뒤까지 유효

        share(2, id, 168)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("확인서 유효기한(2026-10-21)을 넘겨서 공유할 수 없어요"));
        share(2, id, 1).andExpect(status().isCreated()); // 유효기한 안이면 만들어진다
    }

    @Test
    void 확인서_유효_판단과_같이_날짜_단위로_한도를_본다() throws Exception {
        long id = insertCertificate(2, "2026-10-20 00:00:00"); // 만료일 10-20 의 0시 (날짜 단위로는 10-20 까지 유효)

        share(2, id, 24).andExpect(status().isCreated()); // 지금부터 24시간 뒤는 언제나 10-20 이라 만료일 안이다
        share(2, id, 48).andExpect(status().isBadRequest()); // 10-21 은 만료일 밖
    }

    @Test
    void 유효기한을_넘기는_요청은_토큰을_저장하지_않는다() throws Exception {
        long id = insertCertificate(2, "2026-10-21 10:00:00");

        share(2, id, 168).andExpect(status().isBadRequest());

        assertNull(savedToken(id));
    }

    @Test
    void 만료된_확인서는_410() throws Exception {
        long id = insertCertificate(2, "2026-10-18 23:59:59");

        share(2, id, 24)
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.message").value("만료된 확인서예요"));
    }

    @Test
    void 남의_확인서와_없는_확인서는_404() throws Exception {
        long id = issueForBororo();

        share(2, id, 24).andExpect(status().isNotFound()); // 루피가 뽀로로의 확인서를
        share(4, 999999, 24).andExpect(status().isNotFound());
    }

    @Test
    void 부모가_만들면_403() throws Exception {
        long id = issueForBororo();

        share(3, id, 24)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("자녀만 조회할 수 있어요"));
    }
}
