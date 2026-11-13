package com.cheotdanchu.controller;

import com.cheotdanchu.common.config.PhotoStorage;
import com.jayway.jsonpath.JsonPath;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 시드 기준: 미션 3(설거지, NEW, 사진 인증), 미션 2(영수증, 이미 SUBMITTED). 사진은 build/ 아래에 저장해서 uploads/를 더럽히지 않는다 */
@SpringBootTest(properties = "app.upload-dir=build/test-uploads")
@AutoConfigureMockMvc
@Transactional
class MissionSubmitApiTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    EntityManager em;

    @Autowired
    PhotoStorage photoStorage;

    private final MockMultipartFile photo =
            new MockMultipartFile("photos", "dish.jpg", "image/jpeg", new byte[]{1, 2, 3});

    @Test
    void 자녀가_사진과_함께_제출하면_확인_대기가_된다() throws Exception {
        String body = mockMvc.perform(multipart("/missions/3/submissions").file(photo).header("X-Persona-Id", 2))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.missionStatus").value("SUBMITTED"))
                .andReturn().getResponse().getContentAsString();
        Long submissionId = ((Number) JsonPath.read(body, "$.data.submissionId")).longValue();

        em.flush(); // 테스트 트랜잭션 안이라 커밋이 없으니, 상태 변경을 DB에 반영한 뒤 확인한다
        assertEquals("SUBMITTED", jdbc.queryForObject("SELECT status FROM missions WHERE mission_id = 3", String.class));
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM submission_photos WHERE submission_id = ?", Integer.class, submissionId));
    }

    @Test
    void 여러_장_중_하나라도_이미지가_아니면_아무것도_저장하지_않는다() throws Exception {
        MockMultipartFile text = new MockMultipartFile("photos", "a.txt", "text/plain", new byte[]{1});
        long before = savedFileCount();

        mockMvc.perform(multipart("/missions/3/submissions").file(photo).file(text).header("X-Persona-Id", 2))
                .andExpect(status().isBadRequest());

        assertEquals(before, savedFileCount());
    }

    private long savedFileCount() throws IOException {
        Path dir = photoStorage.dir();
        if (!Files.exists(dir)) return 0;
        try (Stream<Path> files = Files.list(dir)) {
            return files.count();
        }
    }

    @Test
    void 사진_인증_미션에_사진이_없으면_400() throws Exception {
        mockMvc.perform(multipart("/missions/3/submissions").header("X-Persona-Id", 2))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("인증 사진을 올려 주세요."));
    }

    @Test
    void 이미지가_아닌_파일은_400() throws Exception {
        MockMultipartFile text = new MockMultipartFile("photos", "a.txt", "text/plain", new byte[]{1});
        mockMvc.perform(multipart("/missions/3/submissions").file(text).header("X-Persona-Id", 2))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 이미_확인_대기_중이면_409() throws Exception {
        mockMvc.perform(multipart("/missions/2/submissions").file(photo).header("X-Persona-Id", 2))
                .andExpect(status().isConflict());
    }

    // 데모 기준일 10/19 기준으로 10/10에 끝난 미션
    @Test
    void 마감일이_지난_미션은_409() throws Exception {
        jdbc.update("INSERT INTO missions (family_id, title, mission_type, reward_amount, start_date, end_date, repeat_type, "
                + "verify_method, origin, status) VALUES (1, '지난 미션', 'CHORE', 1000, '2026-10-01', '2026-10-10', 'ONCE', "
                + "'PHOTO', 'PARENT', 'IN_PROGRESS')");
        Long missionId = jdbc.queryForObject("SELECT mission_id FROM missions WHERE title = '지난 미션'", Long.class);

        mockMvc.perform(multipart("/missions/" + missionId + "/submissions").file(photo).header("X-Persona-Id", 2))
                .andExpect(status().isConflict());
    }

    // 아이폰·브라우저에 따라 HEIC를 형식 없이 보내기도 한다
    @Test
    void 형식_없이_온_HEIC_사진도_받는다() throws Exception {
        MockMultipartFile heic = new MockMultipartFile("photos", "IMG_0001.HEIC", "application/octet-stream", new byte[]{1});
        mockMvc.perform(multipart("/missions/3/submissions").file(heic).header("X-Persona-Id", 2))
                .andExpect(status().isCreated());
    }

    @Test
    void 형식_없이_온_파일이_HEIC가_아니면_400() throws Exception {
        MockMultipartFile unknown = new MockMultipartFile("photos", "photo.exe", "application/octet-stream", new byte[]{1});
        mockMvc.perform(multipart("/missions/3/submissions").file(unknown).header("X-Persona-Id", 2))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 부모가_제출하면_403() throws Exception {
        mockMvc.perform(multipart("/missions/3/submissions").file(photo).header("X-Persona-Id", 1))
                .andExpect(status().isForbidden());
    }

    @Test
    void 다른_가족의_미션이면_404() throws Exception {
        mockMvc.perform(multipart("/missions/3/submissions").file(photo).header("X-Persona-Id", 4))
                .andExpect(status().isNotFound());
    }
}
