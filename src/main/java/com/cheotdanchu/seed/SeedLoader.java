package com.cheotdanchu.seed;

import com.cheotdanchu.ai.JsonUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * 서버가 켜질 때 seed.json 의 원장을 DB에 넣는다. (schema.sql 이 매번 테이블을 새로 만들므로 한 번만 실행됨)
 *
 * 지금은 현재 정산 조회(GET /settlements/current)에 필요한 것만 넣는다.
 *  - accounts: 부모·자녀 계좌 (자녀 balance = 이번 달 쓸 수 있는 돈 + 지난달까지 남은 돈)
 *  - settlements: 월별 정산 이력 (진행 중인 달은 청구액을 비워 두고 카드 결제에서 계산)
 *  - card_payments, transfers(용돈·미션 보상): 진행 중인 정산 기간 것만
 * 과거 결제·취소·상환 등은 해당 API를 만들 때 추가한다.
 *
 * families / members 는 data.sql 이 넣는다. 가족은 이름("가족 A")으로 찾는다.
 */
@Component
public class SeedLoader implements ApplicationRunner {

    private final JdbcTemplate jdbc;
    private final List<String> files;

    public SeedLoader(JdbcTemplate jdbc,
                      @Value("${ai.seed.files:seed/family_a/seed.json,seed/family_b/seed.json}") String files) {
        this.jdbc = jdbc;
        this.files = Arrays.stream(files.split(",")).map(String::trim).filter(p -> !p.isEmpty()).toList();
    }

    @Override
    public void run(ApplicationArguments args) {
        for (String path : files) load(JsonUtil.parseObject(read(path)), path);
    }

    @SuppressWarnings("unchecked")
    private void load(Map<String, Object> seed, String path) {
        if (seed == null) throw new IllegalStateException("seed 파일을 해석하지 못했어요: " + path);

        Long familyId = jdbc.queryForObject("SELECT family_id FROM families WHERE name = ?", Long.class,
                "가족 " + seed.get("family"));
        Long parentMemberId = memberId(familyId, "PARENT");
        Long childMemberId = memberId(familyId, "CHILD");

        Map<String, Object> accounts = (Map<String, Object>) seed.get("accounts");
        Map<String, Object> reference = (Map<String, Object>) seed.get("reference");
        Map<String, Object> today = (Map<String, Object>) reference.get("today");
        int pocketNow = intOf(today, "pocket_now");
        int pocketLeftover = intOf(today, "pocket_leftover");

        // 자녀 계좌 전체 잔액 = 이번 달 쓸 수 있는 돈 + 지난달까지 남은 돈. 부모 계좌 잔액은 시드에 없어 0
        Long parentAccountId = insertAccount(parentMemberId, accounts, "parent_account", 0, 0);
        Long childAccountId = insertAccount(childMemberId, accounts, "child_account",
                pocketNow + pocketLeftover, pocketLeftover);

        LocalTime settleTime = LocalTime.parse(str(((Map<String, Object>) seed.get("rules")).get("settle_time")));
        String openCycle = null;
        for (Map<String, Object> m : (List<Map<String, Object>>) reference.get("monthly")) {
            if (insertSettlement(familyId, m, settleTime)) openCycle = str(m.get("cycle"));
        }
        if (openCycle == null) return; // 진행 중인 정산이 없는 가족 (성년 전환 후)

        Long settlementId = jdbc.queryForObject(
                "SELECT settlement_id FROM settlements WHERE family_id = ? AND status = 'SCHEDULED'", Long.class, familyId);
        for (Map<String, Object> l : (List<Map<String, Object>>) seed.get("ledger")) {
            if (!openCycle.equals(str(l.get("cycle")))) continue;
            Timestamp at = Timestamp.valueOf(LocalDate.parse(str(l.get("date"))).atTime(LocalTime.parse(str(l.get("time")))));
            switch (str(l.get("type"))) {
                case "CARD_PAYMENT" -> jdbc.update(
                        "INSERT INTO card_payments (member_id, settlement_id, merchant_name, category, amount, is_canceled, paid_at) "
                                + "VALUES (?, ?, ?, ?, ?, FALSE, ?)",
                        childMemberId, settlementId, str(l.get("merchant")), str(l.get("category")), intOf(l, "amount"), at);
                case "ALLOWANCE", "MISSION_REWARD" -> jdbc.update(
                        "INSERT INTO transfers (from_account_id, to_account_id, type, amount, transferred_at) VALUES (?, ?, ?, ?, ?)",
                        parentAccountId, childAccountId, str(l.get("type")), intOf(l, "amount"), at);
                default -> { } // 취소·상환·자동 정산 등은 해당 API를 만들 때 추가
            }
        }
    }

    private Long memberId(Long familyId, String role) {
        return jdbc.queryForObject("SELECT member_id FROM members WHERE family_id = ? AND role = ?", Long.class, familyId, role);
    }

    private Long insertAccount(Long memberId, Map<String, Object> accounts, String numberKey, int balance, int carryover) {
        jdbc.update("INSERT INTO accounts (member_id, bank_name, account_number, balance, carryover_balance) VALUES (?, ?, ?, ?, ?)",
                memberId, str(accounts.get("bank")), str(accounts.get(numberKey)), balance, carryover);
        return jdbc.queryForObject("SELECT account_id FROM accounts WHERE member_id = ?", Long.class, memberId);
    }

    /** 정산 한 달치를 넣고, 진행 중인 달이면 true. 진행 중인 달은 청구액을 비워 둔다 (결제 합계로 계산) */
    private boolean insertSettlement(Long familyId, Map<String, Object> m, LocalTime settleTime) {
        String[] period = str(m.get("period")).split(" ~ ");
        LocalDate start = LocalDate.parse(period[0]);
        LocalDate end = LocalDate.parse(period[1]);
        String status = str(m.get("status"));
        String resolution = str(m.get("resolution"));

        if ("진행 중".equals(status)) {
            jdbc.update("INSERT INTO settlements (family_id, period_start, period_end, due_at, status) VALUES (?, ?, ?, ?, 'SCHEDULED')",
                    familyId, start, end, Timestamp.valueOf(end.atTime(settleTime)));
            return true;
        }

        String result;
        if ("완납".equals(status)) result = "FULL_PAID";
        else if (resolution.startsWith("스스로")) result = "SELF_RESOLVED";
        else if (resolution.contains("자동 정산")) result = "AUTO_DEDUCTED";
        else throw new IllegalStateException("알 수 없는 정산 결과예요: " + status + " / " + resolution);

        jdbc.update("INSERT INTO settlements (family_id, period_start, period_end, due_at, billed_amount, balance_at_due, paid_amount, status, result) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, 'COMPLETED', ?)",
                familyId, start, end, Timestamp.valueOf(end.atTime(settleTime)),
                intOf(m, "bill"), intOf(m, "balance_before_settle"), intOf(m, "settled"), result);
        return false;
    }

    private static String read(String path) {
        try (InputStream in = SeedLoader.class.getClassLoader().getResourceAsStream(path)) {
            if (in == null) throw new IllegalStateException("seed 파일이 없어요: resources/" + path);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("seed 파일을 읽지 못했어요: resources/" + path, e);
        }
    }

    private static String str(Object o) {
        return o == null ? "" : o.toString();
    }

    /** 숫자여야 하는 값이 비었거나 숫자가 아니면 0으로 넘기지 않고 서버 시작을 멈춘다 (틀린 숫자가 조용히 들어가는 것을 막음) */
    private static int intOf(Map<String, Object> row, String key) {
        if (row.get(key) instanceof Number n) return n.intValue();
        throw new IllegalStateException("seed 값이 숫자가 아니에요: " + key + " = " + row.get(key));
    }
}
