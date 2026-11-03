package com.cheotdanchu.ai;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

/**
 * AI 설정값. application.yml의 groq.*, ai.* 값을 읽어요.
 * Colab 노트북에서 정한 값과 똑같이 맞춰 주세요.
 */
@Component
public class AiProperties {

    private final String apiKey;
    private final String baseUrl;
    private final String model;
    private final String reasoningEffort;
    private final int timeoutSeconds;
    private final int maxRetries;
    private final int topK;
    private final double minScore;
    private final List<String> ragFiles;
    private final int historyTurns;

    public AiProperties(
            @Value("${groq.api-key:}") String apiKey,
            @Value("${groq.base-url:https://api.groq.com/openai/v1}") String baseUrl,
            @Value("${groq.model:openai/gpt-oss-120b}") String model,
            @Value("${groq.reasoning-effort:low}") String reasoningEffort,
            @Value("${groq.timeout-seconds:30}") int timeoutSeconds,
            @Value("${groq.max-retries:3}") int maxRetries,
            @Value("${ai.rag.top-k:3}") int topK,
            @Value("${ai.rag.min-score:0.5}") double minScore,
            @Value("${ai.rag.files:rag/cards/safety.json,rag/cards/service_rules.json,rag/cards/terms_bok700.json}") String ragFiles,
            @Value("${ai.chat.history-turns:10}") int historyTurns) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.model = model;
        this.reasoningEffort = reasoningEffort;
        this.timeoutSeconds = timeoutSeconds;
        this.maxRetries = Math.max(1, maxRetries);
        this.topK = topK;
        this.minScore = minScore;
        this.ragFiles = Arrays.stream(ragFiles.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
        this.historyTurns = historyTurns;
    }

    public String apiKey() { return apiKey; }
    public String baseUrl() { return baseUrl; }
    public String model() { return model; }
    public String reasoningEffort() { return reasoningEffort; }
    public int timeoutSeconds() { return timeoutSeconds; }
    public int maxRetries() { return maxRetries; }
    public int topK() { return topK; }
    public double minScore() { return minScore; }
    public List<String> ragFiles() { return ragFiles; }
    public int historyTurns() { return historyTurns; }
}
