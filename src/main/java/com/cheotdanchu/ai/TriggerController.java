package com.cheotdanchu.ai;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 홈 화면 카드 API. 백엔드의 /home 응답에 넣어도 되고, 이 API를 그대로 써도 돼요.
 *   GET /api/ai/cards?personaId=child_a
 */
@RestController
@RequestMapping("/api/ai")
public class TriggerController {

    public record CardsResponse(String personaId, String audience, String today, List<TriggerCard> cards) {}

    private final TriggerService triggers;
    private final SeedRepository seeds;

    public TriggerController(TriggerService triggers, SeedRepository seeds) {
        this.triggers = triggers;
        this.seeds = seeds;
    }

    @GetMapping("/cards")
    public CardsResponse cards(@RequestParam("personaId") String personaId) {
        SeedView seed = seeds.byPersona(personaId);
        String audience = seeds.audienceOf(personaId);
        return new CardsResponse(personaId, audience, seeds.today().toString(),
                triggers.homeCards(seed, seeds.today(), audience));
    }
}
