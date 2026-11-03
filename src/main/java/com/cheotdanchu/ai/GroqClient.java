package com.cheotdanchu.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Groq API 호출. LLM을 부르는 곳은 이 클래스 하나뿐이에요.
 * 모든 프롬프트가 JSON 한 개로 답하게 되어 있어서, 답을 Map으로 돌려줘요.
 * 실패하면 null을 돌려주고, 부르는 쪽에서 대체 문장을 써요.
 */
@Component
public class GroqClient {

    private static final Logger log = LoggerFactory.getLogger(GroqClient.class);

    /** 이전 대화 한 줄. role은 "user" 또는 "assistant". */
    public record Message(String role, String content) {}

    private final AiProperties props;
    private final HttpClient http;

    public GroqClient(AiProperties props) {
        this.props = props;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public Map<String, Object> chatJson(String system, String user) {
        return chatJson(system, List.of(), user);
    }

    public Map<String, Object> chatJson(String system, List<Message> history, String user) {
        if (props.apiKey().isEmpty()) {
            log.warn("groq.api-key가 비어 있어요. LLM을 부르지 않고 대체 문장을 써요.");
            return null;
        }

        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", system));
        for (Message m : history) {
            messages.add(Map.of("role", m.role(), "content", m.content()));
        }
        messages.add(Map.of("role", "user", "content", user));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", props.model());
        body.put("messages", messages);
        body.put("response_format", Map.of("type", "json_object"));
        body.put("temperature", 0);
        body.put("reasoning_effort", props.reasoningEffort());
        body.put("include_reasoning", false);

        HttpRequest request = HttpRequest.newBuilder(URI.create(props.baseUrl() + "/chat/completions"))
                .timeout(Duration.ofSeconds(props.timeoutSeconds()))
                .header("Authorization", "Bearer " + props.apiKey())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(JsonUtil.write(body), StandardCharsets.UTF_8))
                .build();

        for (int attempt = 1; attempt <= props.maxRetries(); attempt++) {
            try {
                HttpResponse<String> res = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                int status = res.statusCode();

                if (status == 200) {
                    Map<String, Object> parsed = JsonUtil.parseObject(extractJson(contentOf(res.body())));
                    if (parsed != null) return parsed;
                    log.warn("Groq 답이 JSON이 아니에요 (시도 {}/{})", attempt, props.maxRetries());
                } else if (status == 429 || status >= 500 || res.body().contains("json_validate_failed")) {
                    long waitMs = retryAfterMs(res, attempt);
                    log.warn("Groq {} 응답, {}ms 뒤 다시 시도 ({}/{})", status, waitMs, attempt, props.maxRetries());
                    sleep(waitMs);
                } else {
                    log.error("Groq 호출 실패 {}: {}", status, abbreviate(res.body()));
                    return null;
                }
            } catch (IOException e) {
                log.warn("Groq 연결 오류 ({}/{}): {}", attempt, props.maxRetries(), e.getMessage());
                sleep(1000L * attempt);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static String contentOf(String responseBody) {
        Map<String, Object> root = JsonUtil.parseObject(responseBody);
        if (root == null) return null;
        Object choices = root.get("choices");
        if (!(choices instanceof List<?> list) || list.isEmpty()) return null;
        Object first = list.get(0);
        if (!(first instanceof Map<?, ?> choice)) return null;
        Object message = choice.get("message");
        if (!(message instanceof Map<?, ?> msg)) return null;
        Object content = ((Map<String, Object>) msg).get("content");
        return content == null ? null : content.toString();
    }

    /** 혹시 앞뒤에 글이나 ``` 가 붙어 와도 { ... } 부분만 꺼내요. */
    static String extractJson(String text) {
        if (text == null) return null;
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        return (start >= 0 && end > start) ? text.substring(start, end + 1) : text;
    }

    private static long retryAfterMs(HttpResponse<String> res, int attempt) {
        try {
            String header = res.headers().firstValue("retry-after").orElse(null);
            if (header != null) return (long) (Double.parseDouble(header) * 1000) + 200;
        } catch (NumberFormatException ignored) {
            // 숫자가 아니면 기본 대기
        }
        return 2000L * attempt;
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static String abbreviate(String s) {
        return s == null ? "" : (s.length() > 300 ? s.substring(0, 300) + "..." : s);
    }
}
