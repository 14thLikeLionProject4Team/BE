package com.cheotdanchu.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 서버가 켜질 때 resources/rag/*.json 카드를 모두 읽어서 하나의 목록으로 합쳐요.
 * 파일 목록과 순서는 ai.rag.files 설정을 따라요. (점수가 같으면 먼저 읽은 카드가 앞에 와요)
 */
@Component
public class RagRepository {

    private static final Logger log = LoggerFactory.getLogger(RagRepository.class);

    private final List<RagCard> cards;
    private final Map<String, RagCard> byId;

    public RagRepository(AiProperties props) {
        this(load(props.ragFiles()));
    }

    RagRepository(List<RagCard> cards) {
        Map<String, RagCard> map = new LinkedHashMap<>();
        for (RagCard c : cards) {
            if (map.containsKey(c.id())) log.warn("카드 id가 겹쳐요: {}", c.id());
            map.put(c.id(), c);
        }
        this.cards = List.copyOf(cards);
        this.byId = Collections.unmodifiableMap(map);
        log.info("RAG 카드 {}장을 읽었어요", cards.size());
    }

    public List<RagCard> all() { return cards; }

    public RagCard byId(String id) { return byId.get(id); }

    static List<RagCard> load(List<String> files) {
        List<RagCard> out = new ArrayList<>();
        for (String path : files) {
            try (InputStream in = RagRepository.class.getClassLoader().getResourceAsStream(path)) {
                if (in == null) throw new IllegalStateException("RAG 파일이 없어요: resources/" + path);
                for (Map<String, Object> m : JsonUtil.parseArray(in)) out.add(RagCard.fromMap(m));
            } catch (IOException e) {
                throw new IllegalStateException("RAG 파일을 읽지 못했어요: resources/" + path, e);
            }
        }
        return out;
    }
}
