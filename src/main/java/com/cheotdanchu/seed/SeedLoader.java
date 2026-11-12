package com.cheotdanchu.seed;

import com.cheotdanchu.ai.JsonUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 서버가 켜질 때 seed.json 의 원장을 DB에 넣는다. schema.sql 이 매번 테이블을 새로 만든다는 전제라서(spring.sql.init.mode=always),
 * 이미 데이터가 있는지는 확인하지 않는다. mode 를 never 로 바꿔 테이블이 남은 채 켜면 중복 키 오류로 서버가 시작되지 않으니
 * 쓰지 않는다 (README 참고). 적재는 한 트랜잭션이라 도중에 실패하면 전부 되돌려진다.
 *
 * 지금은 정산 조회·부족 예상 점검·부모 홈 정산 요약에 필요한 것만 넣는다.
 *  - accounts: 부모·자녀 계좌 (자녀 balance = 이번 달 쓸 수 있는 돈 + 지난달까지 남은 돈)
 *  - settlements: 월별 정산 이력 (진행 중인 달은 청구액을 비워 두고 카드 결제에서 계산)
 *  - family_settings: 용돈 보내는 날·결제일·미션 상한 비율·카드 한도
 *  - allowance_changes: 용돈 변경 이력 (아직 적용 전인 변경도 포함)
 *  - card_payments, transfers(용돈·미션 보상 입금): 진행 중인 정산 기간 것만
 * 과거 결제·취소·상환 등은 해당 API를 만들 때 추가한다.
 *
 * families / members 는 data.sql 이 넣는다. 가족은 이름("가족 A")으로 찾는다.
 */
@Component
public class SeedLoader implements ApplicationRunner {

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final List<String> files;
    private final SimpleJdbcInsert accountInsert;
    private final SimpleJdbcInsert settlementInsert;

    public SeedLoader(JdbcTemplate jdbc,
                      PlatformTransactionManager txManager,
                      @Value("${ai.seed.files:seed/family_a/seed.json,seed/family_b/seed.json}") String files) {
        this.jdbc = jdbc;
        this.tx = new TransactionTemplate(txManager);
        this.files = Arrays.stream(files.split(",")).map(String::trim).filter(p -> !p.isEmpty()).toList();
        // usingColumns 로 쓸 컬럼을 정해 두지 않으면 나머지 컬럼에 NULL이 들어가 NOT NULL DEFAULT 컬럼이 깨진다.
        // 계좌·정산은 결제·이체를 연결해야 해서 생성된 ID를 바로 돌려받는다 (넣은 뒤 다시 조회하지 않기 위해)
        this.accountInsert = new SimpleJdbcInsert(jdbc).withTableName("accounts")
                .usingColumns("member_id", "bank_name", "account_number", "balance", "carryover_balance")
                .usingGeneratedKeyColumns("account_id");
        this.settlementInsert = new SimpleJdbcInsert(jdbc).withTableName("settlements")
                .usingColumns("family_id", "period_start", "period_end", "due_at",
                        "billed_amount", "balance_at_due", "paid_amount", "status", "result")
                .usingGeneratedKeyColumns("settlement_id");
    }

    @Override
    public void run(ApplicationArguments args) {
        // 전체를 한 트랜잭션으로 묶는다. 중간에 실패하면 이미 넣은 데이터도 되돌려져서 반쯤 적재된 상태가 남지 않는다
        tx.executeWithoutResult(status -> {
            for (String path : files) load(JsonUtil.parseObject(read(path)), path);
        });
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
        long parentAccountId = insertAccount(parentMemberId, accounts, "parent_account", 0, 0);
        long childAccountId = insertAccount(childMemberId, accounts, "child_account",
                pocketNow + pocketLeftover, pocketLeftover);

        Map<String, Object> rules = (Map<String, Object>) seed.get("rules");
        LocalTime settleTime = LocalTime.parse(str(rules.get("settle_time")));
        String openCycle = null;
        Long settlementId = null;
        for (Map<String, Object> m : (List<Map<String, Object>>) reference.get("monthly")) {
            long id = insertSettlement(familyId, m, settleTime);
            if (isOpen(m)) {
                openCycle = str(m.get("cycle"));
                settlementId = id;
            }
        }

        insertFamilySettings(familyId, rules, (Map<String, Object>) seed.get("card"));
        insertAllowanceChanges(familyId, (List<Map<String, Object>>) seed.get("allowance_history"),
                LocalDate.parse(str(today.get("date"))));
        if (settlementId == null) return; // 진행 중인 정산이 없는 가족 (성년 전환 후)

        // 취소·상환 등은 해당 API를 만들 때 추가
        List<Object[]> payments = new ArrayList<>();
        List<Object[]> transfers = new ArrayList<>();
        for (Map<String, Object> l : (List<Map<String, Object>>) seed.get("ledger")) {
            if (!openCycle.equals(str(l.get("cycle")))) continue;
            Timestamp at = Timestamp.valueOf(LocalDate.parse(str(l.get("date"))).atTime(LocalTime.parse(str(l.get("time")))));
            switch (str(l.get("type"))) {
                case "CARD_PAYMENT" -> payments.add(new Object[]{
                        childMemberId, settlementId, str(l.get("merchant")), str(l.get("category")), intOf(l, "amount"), at});
                case "ALLOWANCE", "MISSION_REWARD" -> transfers.add(new Object[]{
                        parentAccountId, childAccountId, str(l.get("type")), intOf(l, "amount"), at});
                default -> { }
            }
        }
        jdbc.batchUpdate("INSERT INTO card_payments (member_id, settlement_id, merchant_name, category, amount, is_canceled, paid_at) "
                + "VALUES (?, ?, ?, ?, ?, FALSE, ?)", payments);
        jdbc.batchUpdate("INSERT INTO transfers (from_account_id, to_account_id, type, amount, transferred_at) "
                + "VALUES (?, ?, ?, ?, ?)", transfers);
    }

    /** 자동이체 여부·공개 범위는 컬럼 기본값(켜짐·총액만)을 쓴다. 미션 상한 비율은 시드의 0.3 을 30(%)으로 바꿔 넣는다 */
    private void insertFamilySettings(Long familyId, Map<String, Object> rules, Map<String, Object> card) {
        jdbc.update("INSERT INTO family_settings (family_id, allowance_day, settlement_day, mission_cap_rate, card_limit) "
                        + "VALUES (?, ?, ?, ?, ?)",
                familyId, intOf(rules, "allowance_day"), intOf(rules, "payday"),
                Math.round(numOf(rules, "mission_cap_ratio") * 100), intOf(card, "limit"));
    }

    /**
     * 용돈 변경 이력을 시드의 allowance_history 에서 넣는다. 첫 줄은 처음 정한 용돈(INITIAL)이다.
     * 아직 적용 전인 변경(적용 시작일이 기준일 이후)은 기준일이 속한 달 1일에 정한 것으로 본다
     * (명세 예시: "10월 1일 70,000→73,000원 · 물가 · AI 제안 · 11월부터").
     */
    private void insertAllowanceChanges(Long familyId, List<Map<String, Object>> history, LocalDate demoToday) {
        Integer prev = null;
        for (Map<String, Object> h : history) {
            LocalDate effectiveFrom = LocalDate.parse(str(h.get("from")) + "-01");
            int amount = intOf(h, "amount");
            LocalDate decidedOn = effectiveFrom.isAfter(demoToday) ? demoToday.withDayOfMonth(1) : effectiveFrom;
            jdbc.update("INSERT INTO allowance_changes (family_id, prev_amount, amount, effective_from, reason, source, created_at) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?)",
                    familyId, prev, amount, effectiveFrom, allowanceReasonOf(h),
                    str(h.get("reason")).contains("AI 제안") ? "AI_SUGGESTION" : "PARENT",
                    Timestamp.valueOf(decidedOn.atTime(9, 0)));
            prev = amount;
        }
    }

    private static String allowanceReasonOf(Map<String, Object> h) {
        String reason = str(h.get("reason"));
        if (reason.isBlank()) return "INITIAL";
        if (reason.contains("물가")) return "INFLATION";
        if (reason.contains("대학") || reason.contains("고3")) return "SCHOOL";
        throw new IllegalStateException("알 수 없는 용돈 변경 이유예요: " + reason);
    }

    private Long memberId(Long familyId, String role) {
        return jdbc.queryForObject("SELECT member_id FROM members WHERE family_id = ? AND role = ?", Long.class, familyId, role);
    }

    private long insertAccount(Long memberId, Map<String, Object> accounts, String numberKey, int balance, int carryover) {
        return accountInsert.executeAndReturnKey(Map.of(
                "member_id", memberId,
                "bank_name", str(accounts.get("bank")),
                "account_number", str(accounts.get(numberKey)),
                "balance", balance,
                "carryover_balance", carryover)).longValue();
    }

    private static boolean isOpen(Map<String, Object> month) {
        return "진행 중".equals(str(month.get("status")));
    }

    /** 정산 한 달치를 넣고 생성된 ID를 돌려준다. 진행 중인 달은 청구액을 비워 둔다 (결제 합계로 계산) */
    private long insertSettlement(Long familyId, Map<String, Object> m, LocalTime settleTime) {
        String[] period = str(m.get("period")).split(" ~ ");
        LocalDate end = LocalDate.parse(period[1]);

        Map<String, Object> row = new HashMap<>();
        row.put("family_id", familyId);
        row.put("period_start", LocalDate.parse(period[0]));
        row.put("period_end", end);
        row.put("due_at", Timestamp.valueOf(end.atTime(settleTime)));
        if (isOpen(m)) {
            row.put("paid_amount", 0);
            row.put("status", "SCHEDULED");
        } else {
            row.put("billed_amount", intOf(m, "bill"));
            row.put("balance_at_due", intOf(m, "balance_before_settle"));
            row.put("paid_amount", intOf(m, "settled"));
            row.put("status", "COMPLETED");
            row.put("result", resultOf(m));
        }
        return settlementInsert.executeAndReturnKey(row).longValue();
    }

    private static String resultOf(Map<String, Object> m) {
        String status = str(m.get("status"));
        String resolution = str(m.get("resolution"));
        if ("완납".equals(status)) return "FULL_PAID";
        if (resolution.startsWith("스스로")) return "SELF_RESOLVED";
        if (resolution.contains("자동 정산")) return "AUTO_DEDUCTED";
        throw new IllegalStateException("알 수 없는 정산 결과예요: " + status + " / " + resolution);
    }

    private static String read(String path) {
        try {
            return new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("seed 파일을 읽지 못했어요: resources/" + path, e);
        }
    }

    private static String str(Object o) {
        return o == null ? "" : o.toString();
    }

    /** 숫자여야 하는 값이 비었거나 숫자가 아니면 0으로 넘기지 않고 서버 시작을 멈춘다 (틀린 숫자가 조용히 들어가는 것을 막음) */
    private static int intOf(Map<String, Object> row, String key) {
        return (int) numOf(row, key);
    }

    private static double numOf(Map<String, Object> row, String key) {
        if (row.get(key) instanceof Number n) return n.doubleValue();
        throw new IllegalStateException("seed 값이 숫자가 아니에요: " + key + " = " + row.get(key));
    }
}
