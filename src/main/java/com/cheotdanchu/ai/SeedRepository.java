package com.cheotdanchu.ai;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * resources/seed/ 의 seed.json (원장 생성기 결과)을 읽어서 페르소나 id로 찾아줘요.
 * 백엔드가 원장을 DB에 따로 넣었다면 이 클래스 대신 그 데이터로 SeedView를 만들어 TriggerService에 넘기면 돼요.
 */
@Component
public class SeedRepository {

    private final List<SeedView> seeds;
    private final LocalDate today;

    public SeedRepository(
            @Value("${ai.seed.files:seed/family_a/seed.json,seed/family_b/seed.json}") String files,
            @Value("${ai.demo.today:}") String today) {
        List<SeedView> list = new ArrayList<>();
        for (String path : Arrays.stream(files.split(",")).map(String::trim).filter(p -> !p.isEmpty()).toList()) {
            list.add(new SeedView(JsonUtil.parseObject(read(path))));
        }
        this.seeds = List.copyOf(list);
        this.today = today.isBlank()
                ? (seeds.isEmpty() ? LocalDate.now() : seeds.get(0).demoToday)
                : LocalDate.parse(today);
    }

    /** 데모 기준일. ai.demo.today가 없으면 seed.json의 demo_today */
    public LocalDate today() {
        return today;
    }

    /** child_a, parent_a 처럼 personas.json의 id로 가족 찾기 */
    public SeedView byPersona(String personaId) {
        for (SeedView s : seeds) {
            if (s.childId.equals(personaId) || s.parentId.equals(personaId)) return s;
        }
        throw new IllegalArgumentException("없는 페르소나예요: " + personaId);
    }

    /** "자녀" 또는 "부모" */
    public String audienceOf(String personaId) {
        return byPersona(personaId).parentId.equals(personaId) ? TriggerService.PARENT : TriggerService.CHILD;
    }

    private static String read(String path) {
        try (InputStream in = SeedRepository.class.getClassLoader().getResourceAsStream(path)) {
            if (in == null) throw new IllegalStateException("seed 파일이 없어요: resources/" + path);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("seed 파일을 읽지 못했어요: resources/" + path, e);
        }
    }
}
