package com.cheotdanchu.ai;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

public final class JsonUtil {

    private static final ObjectMapper MAPPER = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    private JsonUtil() {}

    @SuppressWarnings("unchecked")
    public static Map<String, Object> parseObject(String text) {
        if (text == null) return null;
        try {
            Object value = MAPPER.readValue(text, Object.class);
            return value instanceof Map ? (Map<String, Object>) value : null;
        } catch (Exception e) {
            return null;
        }
    }

    public static List<Map<String, Object>> parseArray(InputStream in) throws IOException {
        return MAPPER.readValue(in, new TypeReference<List<Map<String, Object>>>() {});
    }

    public static String write(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("JSON 변환 실패", e);
        }
    }
}
