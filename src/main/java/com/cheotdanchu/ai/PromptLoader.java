package com.cheotdanchu.ai;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * resources/prompts/*.md 를 읽어서 system 메시지를 만들어요.
 * system = _common.md + 빈 줄 + {name}.md (빈칸 {이름} 을 채운 것)
 * 파일은 처음 한 번 읽고 기억해요. md를 고쳤으면 서버를 다시 켜 주세요.
 */
@Component
public class PromptLoader {

    private final Map<String, String> cache = new ConcurrentHashMap<>();

    public String build(String name, Map<String, String> fills) {
        String body = raw(name);
        for (Map.Entry<String, String> e : fills.entrySet()) {
            body = body.replace("{" + e.getKey() + "}", e.getValue() == null ? "" : e.getValue());
        }
        return raw("_common") + "\n\n" + body;
    }

    public String build(String name) {
        return build(name, Map.of());
    }

    String raw(String name) {
        return cache.computeIfAbsent(name, n -> read("prompts/" + n + ".md"));
    }

    static String read(String path) {
        try (InputStream in = PromptLoader.class.getClassLoader().getResourceAsStream(path)) {
            if (in == null) throw new IllegalStateException("파일이 없어요: resources/" + path);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("파일을 읽지 못했어요: resources/" + path, e);
        }
    }
}
