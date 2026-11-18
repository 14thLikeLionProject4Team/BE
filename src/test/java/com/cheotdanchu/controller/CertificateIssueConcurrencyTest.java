package com.cheotdanchu.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * 발급 버튼을 연타해도(동시 요청) 확인서와 PDF 는 한 장만 만들어져야 한다.
 * 여러 요청이 각자 트랜잭션을 가져야 해서 롤백되는 테스트가 아니고, 끝나면 만든 확인서 행과 PDF 를 직접 지운다.
 * 가족 B 뽀로로(id 4)는 시드에 확인서가 없고 요건을 이미 채웠다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CertificateIssueConcurrencyTest {

    private static final int REQUESTS = 5;

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbc;

    @Value("${app.certificate-dir}")
    String certificateDir;

    @Test
    void 발급을_동시에_여러_번_눌러도_한_장만_발급된다() throws Exception {
        Path dir = Path.of(certificateDir).toAbsolutePath();
        long filesBefore = countFiles(dir);
        try {
            List<Integer> statuses = issueConcurrently();

            assertEquals(1, statuses.stream().filter(s -> s == 201).count(), "발급된 요청은 한 번뿐이어야 해요: " + statuses);
            assertEquals(REQUESTS - 1, statuses.stream().filter(s -> s == 409).count(), "나머지는 이미 발급됨(409)이어야 해요: " + statuses);
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM certificates WHERE member_id = 4", Integer.class));
            assertEquals(filesBefore + 1, countFiles(dir), "PDF 도 한 장만 만들어져야 해요");
        } finally {
            for (String name : jdbc.queryForList("SELECT file_path FROM certificates WHERE member_id = 4", String.class)) {
                Files.deleteIfExists(dir.resolve(name));
            }
            jdbc.update("DELETE FROM certificates WHERE member_id = 4");
        }
    }

    private static long countFiles(Path dir) throws IOException {
        if (!Files.exists(dir)) return 0;
        try (Stream<Path> files = Files.list(dir)) {
            return files.count();
        }
    }

    private List<Integer> issueConcurrently() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(REQUESTS);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Integer>> futures = new ArrayList<>();
            for (int i = 0; i < REQUESTS; i++) {
                Callable<Integer> request = () -> {
                    start.await(); // 모든 요청이 준비된 뒤 한꺼번에 보낸다
                    return mockMvc.perform(post("/child/certificates").header("X-Persona-Id", 4)
                                    .contentType(MediaType.APPLICATION_JSON).content("{\"identityVerified\":true}"))
                            .andReturn().getResponse().getStatus();
                };
                futures.add(pool.submit(request));
            }
            start.countDown();
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> f : futures) statuses.add(f.get());
            return statuses;
        } finally {
            pool.shutdownNow();
        }
    }
}
