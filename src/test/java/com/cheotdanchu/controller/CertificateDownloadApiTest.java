package com.cheotdanchu.controller;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 시연 기준일 2026-10-19. 가족 B 뽀로로(id 4)는 바로 발급할 수 있고, 가족 A 루피(id 2)는 아직 못 한다.
 * 발급한 PDF 는 롤백과 함께 지워진다. 테스트마다 롤백된다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CertificateDownloadApiTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbc;

    @Value("${app.certificate-dir}")
    String certificateDir;

    private ResultActions download(int personaId, long certificateId) throws Exception {
        return mockMvc.perform(get("/child/certificates/" + certificateId + "/download").header("X-Persona-Id", personaId));
    }

    /** 뽀로로가 확인서를 실제로 발급하고 그 ID 를 돌려준다 */
    private long issueForBororo() throws Exception {
        String body = mockMvc.perform(post("/child/certificates").header("X-Persona-Id", 4)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"identityVerified\":true}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.data.certificateId")).longValue();
    }

    private long insertCertificate(int memberId, String filePath, String expiresAt) {
        jdbc.update("INSERT INTO certificates (member_id, full_paid_months, total_months, max_streak, avg_margin_rate, "
                + "self_resolved_count, shortfall_count, file_path, issued_at, expires_at) "
                + "VALUES (?, 12, 12, 12, 7.5, 0, 1, ?, '2026-07-01 10:00:00', ?)", memberId, filePath, expiresAt);
        return jdbc.queryForObject("SELECT MAX(certificate_id) FROM certificates", Long.class);
    }

    @Test
    void 발급한_확인서를_PDF_파일로_내려받는다() throws Exception {
        long id = issueForBororo();

        byte[] body = download(4, id)
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"certificate_" + id + ".pdf\""))
                .andReturn().getResponse().getContentAsByteArray();

        assertTrue(body.length > 1000, "PDF 내용이 비어 있어요");
        assertEquals("%PDF", new String(body, 0, 4));
    }

    @Test
    void 내려받은_파일은_발급할_때_저장한_PDF와_같다() throws Exception {
        long id = issueForBororo();
        String fileName = jdbc.queryForObject("SELECT file_path FROM certificates WHERE certificate_id = ?", String.class, id);
        byte[] saved = Files.readAllBytes(Path.of(certificateDir).toAbsolutePath().resolve(fileName));

        byte[] downloaded = download(4, id).andReturn().getResponse().getContentAsByteArray();

        assertArrayEquals(saved, downloaded); // 내려받을 때마다 새로 만들지 않고 저장한 파일을 그대로 준다
    }

    @Test
    void 없는_확인서는_404() throws Exception {
        download(4, 999999)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("발급한 확인서가 없어요"));
    }

    @Test
    void 남의_확인서는_없는_것과_같이_404() throws Exception {
        long id = issueForBororo();

        download(2, id).andExpect(status().isNotFound());
    }

    @Test
    void 만료된_확인서는_410() throws Exception {
        long id = insertCertificate(2, "expired.pdf", "2026-10-18 23:59:59");

        download(2, id)
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("만료된 확인서예요"));
    }

    @Test
    void 만료일_당일에는_내려받을_수_있다() throws Exception {
        // 확인서를 JDBC 로 직접 넣는다 (발급 API 로 만든 확인서는 같은 트랜잭션의 영속성 컨텍스트에 남아 DB 변경이 조회에 안 보인다)
        issueForBororo();
        String savedPdf = jdbc.queryForObject("SELECT file_path FROM certificates WHERE member_id = 4", String.class);
        long id = insertCertificate(2, savedPdf, "2026-10-19 00:00:01");

        download(2, id).andExpect(status().isOk()); // 날짜 단위 기준일이라 만료일 당일까지 유효
    }

    @Test
    void 서버에_PDF_파일이_없으면_404() throws Exception {
        long id = insertCertificate(2, "missing.pdf", "2027-01-01 10:00:00");

        download(2, id)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("확인서 파일을 찾을 수 없어요"));
    }

    @Test
    void 파일_이름이_폴더_밖을_가리켜도_읽지_않는다() throws Exception {
        long id = insertCertificate(2, "../../../build.gradle", "2027-01-01 10:00:00");

        download(2, id)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("확인서 파일을 찾을 수 없어요"));
    }

    @Test
    void 파일_이름에_경로로_쓸_수_없는_문자가_있어도_500이_아니라_404() throws Exception {
        long id = insertCertificate(2, "a*b?.pdf", "2027-01-01 10:00:00"); // Windows 에서는 경로로 만들 수 없는 이름

        download(2, id)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("확인서 파일을 찾을 수 없어요"));
    }

    @Test
    void 개인_기록이라_캐시하지_않도록_알린다() throws Exception {
        long id = issueForBororo();

        download(4, id).andExpect(header().string("Cache-Control", "no-store"));
    }

    @Test
    void 부모가_내려받으면_403() throws Exception {
        long id = issueForBororo();

        download(3, id)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("자녀만 조회할 수 있어요"));
    }
}
