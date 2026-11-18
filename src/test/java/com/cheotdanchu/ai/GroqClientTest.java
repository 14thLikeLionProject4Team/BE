package com.cheotdanchu.ai;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class GroqClientTest {

    private static final String BROKEN = "돈을 �릴지";
    private static final String CLEAN = "돈을 빌릴지";

    private final AtomicInteger calls = new AtomicInteger();
    private HttpServer server;

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void 깨진_글자가_있는_답은_버리고_다시_요청한다() throws IOException {
        GroqClient client = clientAnswering(List.of(BROKEN, CLEAN));

        Map<String, Object> result = client.chatJson("system", "user");

        assertThat(result).containsEntry("answer", CLEAN);
        assertThat(calls).hasValue(2);
    }

    @Test
    void 끝까지_깨진_답만_오면_null을_돌려준다() throws IOException {
        GroqClient client = clientAnswering(List.of(BROKEN, BROKEN, BROKEN));

        assertThat(client.chatJson("system", "user")).isNull();
        assertThat(calls).hasValue(3);
    }

    /** 호출 순서대로 answers 를 돌려주는 가짜 Groq 서버에 연결한다 */
    private GroqClient clientAnswering(List<String> answers) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/chat/completions", exchange -> {
            String answer = answers.get(calls.getAndIncrement());
            String body = "{\"choices\":[{\"message\":{\"content\":\"{\\\"answer\\\":\\\"" + answer + "\\\"}\"}}]}";
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();

        String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        return new GroqClient(new AiProperties("test-key", baseUrl, "model", "low", 5, 3, 3, 0.5, "", 10));
    }
}
