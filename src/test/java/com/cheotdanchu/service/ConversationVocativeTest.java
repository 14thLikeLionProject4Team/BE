package com.cheotdanchu.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ConversationVocativeTest {

    @Test
    void 받침이_있으면_아_없으면_야() {
        assertEquals("아", ConversationService.vocative("지민"));
        assertEquals("야", ConversationService.vocative("루피"));
        assertEquals("야", ConversationService.vocative("Amy"));
        assertEquals("야", ConversationService.vocative(""));
    }
}
