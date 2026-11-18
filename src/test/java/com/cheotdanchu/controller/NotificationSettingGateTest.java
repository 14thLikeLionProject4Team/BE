package com.cheotdanchu.controller;

import com.cheotdanchu.entity.Notification;
import com.cheotdanchu.repository.MemberRepository;
import com.cheotdanchu.service.NotificationSender;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 끈 알림 항목은 그 알림이 만들어지지 않는다. 알림을 만드는 곳(미션·용돈 변경)을 실제 API 로 일으켜 확인한다. 테스트마다 롤백된다 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class NotificationSettingGateTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    EntityManager em;

    @Autowired
    NotificationSender sender;

    @Autowired
    MemberRepository memberRepository;

    private void turn(int personaId, String type, boolean enabled) throws Exception {
        mockMvc.perform(patch("/notification-settings/" + type).header("X-Persona-Id", personaId)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":" + enabled + "}"))
                .andExpect(status().isOk());
    }

    private int count(int memberId, String type) {
        em.flush();
        return jdbc.queryForObject("SELECT COUNT(*) FROM notifications WHERE member_id = ? AND type = ?", Integer.class, memberId, type);
    }

    private void remind() throws Exception {
        mockMvc.perform(post("/missions/submissions/5/remind").header("X-Persona-Id", 2)).andExpect(status().isOk());
    }

    private void changeAllowance() throws Exception {
        mockMvc.perform(post("/allowance/changes").header("X-Persona-Id", 1).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":73000,\"reason\":\"INFLATION\",\"allowanceDay\":1,\"autoTransfer\":true,\"settlementDay\":25}"))
                .andExpect(status().isCreated());
    }

    private void approve() throws Exception {
        mockMvc.perform(post("/missions/submissions/5/approve").header("X-Persona-Id", 1)).andExpect(status().isOk());
    }

    private void reject() throws Exception {
        mockMvc.perform(post("/missions/submissions/5/reject").header("X-Persona-Id", 1)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"PHOTO_UNCLEAR\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void 켜져_있으면_알림이_만들어진다() throws Exception {
        int before = count(1, "MISSION_REMIND");

        remind();

        assertEquals(before + 1, count(1, "MISSION_REMIND"));
    }

    @Test
    void 부모가_미션_확인_재요청을_끄면_아이가_다시_알려도_알림이_안_만들어진다() throws Exception {
        turn(1, "MISSION_REMIND", false);
        int before = count(1, "MISSION_REMIND");

        remind(); // 아이 쪽은 성공 응답이지만 부모에게는 알림이 가지 않는다

        assertEquals(before, count(1, "MISSION_REMIND"));
    }

    @Test
    void 아이가_용돈_변경을_끄면_용돈을_바꿔도_알림이_안_만들어지고_이력은_남는다() throws Exception {
        turn(2, "ALLOWANCE_CHANGED", false);
        int before = count(2, "ALLOWANCE_CHANGED");
        int changesBefore = jdbc.queryForObject("SELECT COUNT(*) FROM allowance_changes WHERE family_id = 1", Integer.class);

        changeAllowance();

        assertEquals(before, count(2, "ALLOWANCE_CHANGED"));
        // 알림만 빠지고 변경은 저장된다
        assertEquals(changesBefore + 1, jdbc.queryForObject("SELECT COUNT(*) FROM allowance_changes WHERE family_id = 1", Integer.class));
    }

    @Test
    void 아이가_용돈_변경을_켜_두면_알림이_만들어진다() throws Exception {
        int before = count(2, "ALLOWANCE_CHANGED");

        changeAllowance();

        assertEquals(before + 1, count(2, "ALLOWANCE_CHANGED"));
    }

    @Test
    void 아이가_미션_보상을_끄면_승인해도_알림이_안_만들어지고_보상은_들어간다() throws Exception {
        turn(2, "MISSION_REWARD", false);
        int before = count(2, "MISSION_REWARD");
        int balanceBefore = jdbc.queryForObject("SELECT balance FROM accounts WHERE member_id = 2", Integer.class);

        approve();

        assertEquals(before, count(2, "MISSION_REWARD"));
        em.flush();
        assertTrue(jdbc.queryForObject("SELECT balance FROM accounts WHERE member_id = 2", Integer.class) > balanceBefore,
                "알림만 빠지고 보상은 들어가야 해요");
    }

    @Test
    void 아이가_미션_반려를_끄면_반려해도_알림이_안_만들어진다() throws Exception {
        turn(2, "MISSION_REJECTED", false);
        int before = count(2, "MISSION_REJECTED");

        reject();

        assertEquals(before, count(2, "MISSION_REJECTED"));
    }

    private boolean sendToChild(Notification.Type type) {
        return sender.send(Notification.of(memberRepository.getPersona(2L), type, "제목", "내용", 1L, LocalDateTime.of(2026, 10, 19, 9, 0)));
    }

    @Test
    void 결제일_정산_결과_항목을_끄면_부족분_해소와_자동_정산_알림도_안_만들어진다() throws Exception {
        turn(2, "SETTLEMENT_RESULT", false);

        assertFalse(sendToChild(Notification.Type.SETTLEMENT_RESULT));
        assertFalse(sendToChild(Notification.Type.SHORTFALL_RESOLVED));
        assertFalse(sendToChild(Notification.Type.AUTO_DEDUCT));
        assertTrue(sendToChild(Notification.Type.ALLOWANCE_CHANGED)); // 다른 항목은 영향이 없다
    }

    @Test
    void 결제일_정산_결과_항목이_켜져_있으면_정산_쪽_알림이_만들어진다() throws Exception {
        int before = count(2, "SHORTFALL_RESOLVED");

        assertTrue(sendToChild(Notification.Type.SHORTFALL_RESOLVED));

        assertEquals(before + 1, count(2, "SHORTFALL_RESOLVED"));
    }

    @Test
    void 부모의_부족_예상_알림은_기본으로_꺼져_있어_켜야_만들어진다() throws Exception {
        Notification toParent = Notification.of(memberRepository.getPersona(1L), Notification.Type.SHORTFALL_EXPECTED, "제목", "내용", 1L,
                LocalDateTime.of(2026, 10, 19, 9, 0));
        assertFalse(sender.send(toParent));

        turn(1, "SHORTFALL_EXPECTED", true);
        assertTrue(sender.send(toParent));
    }

    private void createMission() throws Exception {
        mockMvc.perform(post("/missions").header("X-Persona-Id", 1).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"베란다 화분 물 주기\",\"missionType\":\"CHORE\",\"rewardAmount\":1000,"
                                + "\"startDate\":\"2026-10-19\",\"endDate\":\"2026-11-15\",\"repeatType\":\"WEEKLY\",\"verifyMethod\":\"PHOTO\"}"))
                .andExpect(status().isCreated());
    }

    private void propose() throws Exception {
        mockMvc.perform(post("/missions/proposals").header("X-Persona-Id", 2).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"강아지 산책\",\"missionType\":\"CHORE\",\"desiredReward\":2000,"
                                + "\"startDate\":\"2026-10-19\",\"endDate\":\"2026-10-25\",\"repeatType\":\"DAILY\",\"verifyMethod\":\"PHOTO\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    void 아이가_새_미션을_끄면_부모가_미션을_만들어도_알림이_안_만들어지고_미션은_생긴다() throws Exception {
        turn(2, "NEW_MISSION", false);
        int before = count(2, "NEW_MISSION");
        int missionsBefore = jdbc.queryForObject("SELECT COUNT(*) FROM missions", Integer.class);

        createMission();

        assertEquals(before, count(2, "NEW_MISSION"));
        em.flush();
        assertEquals(missionsBefore + 1, jdbc.queryForObject("SELECT COUNT(*) FROM missions", Integer.class)); // 알림만 빠지고 미션은 만들어진다
    }

    @Test
    void 아이가_새_미션을_켜_두면_부모가_미션을_만들_때_알림이_만들어진다() throws Exception {
        int before = count(2, "NEW_MISSION");

        createMission();

        assertEquals(before + 1, count(2, "NEW_MISSION"));
    }

    @Test
    void 부모가_미션_제안을_끄면_아이가_제안해도_알림이_안_만들어지고_제안은_저장된다() throws Exception {
        turn(1, "MISSION_PROPOSED", false);
        int before = count(1, "MISSION_PROPOSED");
        int missionsBefore = jdbc.queryForObject("SELECT COUNT(*) FROM missions", Integer.class);

        propose();

        assertEquals(before, count(1, "MISSION_PROPOSED"));
        em.flush();
        assertEquals(missionsBefore + 1, jdbc.queryForObject("SELECT COUNT(*) FROM missions", Integer.class));
    }

    @Test
    void 부모가_미션_제안을_켜_두면_아이가_제안할_때_알림이_만들어진다() throws Exception {
        int before = count(1, "MISSION_PROPOSED");

        propose();

        assertEquals(before + 1, count(1, "MISSION_PROPOSED"));
    }

    @Test
    void 끈_항목은_다시_켜면_이후_알림이_만들어진다() throws Exception {
        turn(1, "MISSION_REMIND", false);
        turn(1, "MISSION_REMIND", true);
        int before = count(1, "MISSION_REMIND");

        remind();

        assertEquals(before + 1, count(1, "MISSION_REMIND"));
    }
}
