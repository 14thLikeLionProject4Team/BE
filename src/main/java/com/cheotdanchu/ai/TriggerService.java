package com.cheotdanchu.ai;

import org.springframework.stereotype.Service;

import java.text.NumberFormat;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 트리거 엔진 (트리거 명세 MVP의 P0).
 *
 * 하는 일: 원장(seed.json)의 첫날부터 today까지 하루씩 되감으면서, 그날 발동하는 트리거를 카드로 만들어요.
 *   - 언제 띄울지는 전부 이 코드가 판단해요. LLM은 판단하지 않아요.
 *   - 문장은 대부분 틀 문장이고, T-04(브리핑)·T-05(용돈 제안)·T-12(회고)만 MonthlyTextService로 LLM 문장을 받아요.
 *   - LLM 문장은 화면에 보이는 카드만, 처음 보여줄 때 한 번 만들어요. (지난 카드까지 다 만들면 LLM을 수십 번 불러서)
 *
 * 같은 날 순서는 명세 5번을 따라요.
 *   1일 : T-02 용돈 입금 → T-03 부족분 자동 회수 → T-04 브리핑 → T-05 용돈 제안
 *   19일: T-07 결제일 D-6 점검
 *   25일: T-09 부족분 발생 → T-10 재협상 → T-11 발급까지 남은 횟수 → (T-17)
 *   26일: T-12 월말 회고
 *   아무 날: T-13 부족분 해소 (원장에 송금 기록이 생긴 날)
 *   생일 : T-16 성년 전환 → T-17 확인서 발급 가능
 * T-08(자동 정산)은 카드가 없고, T-15(대화 위기)는 ChatFlowService에 있어요.
 */
@Service
public class TriggerService {

    public static final String CHILD = "자녀";
    public static final String PARENT = "부모";
    private static final int REQUIRED_SETTLEMENTS = 12;

    private final MonthlyTextService monthlyText;
    private final Map<String, List<TriggerCard>> firedCache = new ConcurrentHashMap<>();
    private final Map<String, TriggerCard> renderedCache = new ConcurrentHashMap<>();

    public TriggerService(MonthlyTextService monthlyText) {
        this.monthlyText = monthlyText;
    }

    // ───────────────────────── 화면용 ─────────────────────────

    /**
     * 홈 화면에 보여줄 카드. 화면 위 순서대로 정렬돼 있고, LLM 카드는 문장까지 채워져 있어요.
     * audience: "자녀" 또는 "부모"
     */
    public List<TriggerCard> homeCards(SeedView seed, LocalDate today, String audience) {
        List<TriggerCard> out = new ArrayList<>();
        for (TriggerCard c : visible(seed, today, audience)) out.add(render(c));
        return out;
    }

    /**
     * 보여줄 카드 고르기 (문장은 아직 안 채움).
     *   - 마지막 결제일(25일)부터 오늘까지 생긴 카드
     *   - T-16·T-17은 한 번 생기면 계속
     *   - 만 19세가 지나면 부모에게는 T-16만 (기록은 본인만 봐요)
     */
    public List<TriggerCard> visible(SeedView seed, LocalDate today, String audience) {
        LocalDate lastPayday = today.getDayOfMonth() >= seed.payday
                ? today.withDayOfMonth(seed.payday)
                : today.minusMonths(1).withDayOfMonth(seed.payday);
        boolean adult = seed.ageOn(today) >= 19;

        return fired(seed, today).stream()
                .filter(c -> c.audience().equals(audience))
                .filter(c -> !(PARENT.equals(audience) && adult && !"T-16".equals(c.trigger())))
                .filter(c -> Set.of("T-16", "T-17").contains(c.trigger())
                        || !LocalDate.parse(c.date()).isBefore(lastPayday))
                .sorted(Comparator.comparingInt(TriggerCard::group)
                        .thenComparing(TriggerCard::date, Comparator.reverseOrder()))
                .toList();
    }

    /** LLM 카드면 문장을 만들어 채워요. 틀 카드는 그대로. 한 번 만든 문장은 기억해요. */
    public TriggerCard render(TriggerCard c) {
        if (!"LLM 대기".equals(c.textType())) return c;
        return renderedCache.computeIfAbsent(c.key(), k -> {
            String cacheKey = c.childId() + ":" + c.trigger() + ":" + c.period();
            Map<String, Object> r;
            Map<String, Object> extra = new LinkedHashMap<>();
            String title;
            String body;
            switch (c.trigger()) {
                case "T-12" -> {
                    r = monthlyText.review(cacheKey, c.facts());
                    title = s(r.get("headline"));
                    body = s(r.get("body"));
                    extra.put("next_tip", r.get("next_tip"));
                }
                case "T-04" -> {
                    r = monthlyText.briefing(cacheKey, c.facts());
                    title = s(r.get("headline"));
                    body = s(r.get("body"));
                    extra.put("suggestion", r.get("suggestion"));
                }
                case "T-05" -> {
                    r = monthlyText.decisionCard(cacheKey, c.facts());
                    title = s(r.get("title"));
                    body = s(r.get("summary"));
                    extra.put("reasons", r.get("reasons"));
                    extra.put("note", r.get("note"));
                    extra.put("proposed_allowance", c.facts().get("proposed_allowance"));
                    extra.put("apply_from", c.facts().get("apply_from"));
                }
                default -> {
                    return c;
                }
            }
            String type = Boolean.TRUE.equals(r.get("_fallback")) ? "틀(LLM 실패)" : "LLM";
            return c.withText(title, body, extra, type);
        });
    }

    // ───────────────────────── 되감기 ─────────────────────────

    /** 원장 첫날부터 today까지 생긴 모든 카드 (생긴 순서). 문장은 틀 카드만 채워져 있어요. */
    public List<TriggerCard> fired(SeedView seed, LocalDate today) {
        return firedCache.computeIfAbsent(seed.family + "|" + today, k -> replay(seed, today));
    }

    List<TriggerCard> replay(SeedView s, LocalDate today) {
        Map<LocalDate, List<SeedView.Line>> byDate = new LinkedHashMap<>();
        for (SeedView.Line l : s.ledger) byDate.computeIfAbsent(l.date(), d -> new ArrayList<>()).add(l);

        Out out = new Out();
        long[] pocket = {0};   // 0번 주머니(이번 달 정산에 쓰는 돈) 잔액
        int settled = 0;       // 지금까지 결제일 정산 횟수

        LocalDate start = s.ledger.isEmpty() ? today : s.ledger.get(0).date();
        for (LocalDate d = start; !d.isAfter(today); d = d.plusDays(1)) {
            List<SeedView.Line> day = byDate.getOrDefault(d, List.of());

            // 그날 원장을 먼저 반영
            for (SeedView.Line l : day) {
                if (s.pocketNow.equals(l.to())) pocket[0] += l.amount();
                if (s.pocketNow.equals(l.from())) pocket[0] -= l.amount();
                if ("SETTLEMENT".equals(l.type())) settled++;
            }

            if (d.getDayOfMonth() == s.allowanceDay) onAllowanceDay(s, d, day, out);
            if (d.getDayOfMonth() == s.payday - 6) onDminus6(s, d, pocket[0], out);
            if (d.getDayOfMonth() == s.payday) onPayday(s, d, day, settled, out);
            if (d.getDayOfMonth() == s.payday + 1) onDayAfterPayday(s, d, out);
            onRepay(s, d, day, out);
            if (d.equals(s.birth.plusYears(19))) onAdult(s, d, settled, out);
        }
        return out.cards;
    }

    // 1일: T-02 → T-03 → T-04 → T-05
    private void onAllowanceDay(SeedView s, LocalDate d, List<SeedView.Line> day, Out out) {
        String month = SeedView.ym(d);
        boolean allowanceCame = false;
        for (SeedView.Line l : day) {
            if ("ALLOWANCE".equals(l.type())) {
                allowanceCame = true;
                out.add(card(s, "T-02", CHILD, month, d, 5, "용돈 입금",
                        d.getMonthValue() + "월 용돈 " + won(l.amount()) + "이 '" + s.pocketNow + "'에 들어왔어요.",
                        Map.of("amount", l.amount())));
            }
        }
        for (SeedView.Line l : day) {
            if ("AUTO_RECOVERY".equals(l.type())) {
                String text = "지난달 부족분 " + won(l.amount()) + "이 이번 용돈에서 자동 정산됐어요.";
                Map<String, Object> extra = Map.of("amount", l.amount(), "for_cycle", l.forCycle());
                out.add(card(s, "T-03", CHILD, l.forCycle(), d, 2, "지난달 부족분 자동 정산", text, extra));
                out.add(card(s, "T-03", PARENT, l.forCycle(), d, 2, "지난달 부족분 자동 정산", text, extra));
            }
        }
        if (!allowanceCame) return;   // 기록이 끝난 가족은 브리핑·제안 없음

        String prev = SeedView.ym(d.minusMonths(1));
        Map<String, Object> m = s.monthlyOf(prev);
        if (m != null && !"진행 중".equals(SeedView.str(m.get("status")))) {
            out.add(llmCard(s, "T-04", PARENT, prev, d, 4, "월간 브리핑", briefingFacts(s, m, d)));
        }

        Map<String, Object> proposal = upcomingProposal(s, d);
        if (proposal != null && (d.getMonthValue() - 1) % 3 == 0) {   // 분기 첫 용돈일 (1·4·7·10월)
            out.add(llmCard(s, "T-05", PARENT, month, d, 3, "용돈 제안", decisionFacts(s, d, proposal)));
        }
    }

    // 19일: T-07 (자녀에게만)
    private void onDminus6(SeedView s, LocalDate d, long pocket, Out out) {
        String cycle = s.cycleOf(d);
        long bill = billUntil(s, cycle, d);
        long gap = bill - pocket;
        if (gap <= 0) return;
        String body = "청구 예정액이 '" + s.pocketNow + "'보다 " + won(gap) + " 많아요. "
                + "지금대로면 결제일에 " + won(gap) + "이 모자랄 수 있어요. "
                + "모자라도 벌칙 없이 다음 용돈 전까지 보내면 돼요. 다른 사람에게 빌리지 마세요.";
        Map<String, Object> extra = new LinkedHashMap<>();
        extra.put("gap", gap);
        extra.put("bill", bill);
        extra.put("pocket", pocket);
        extra.put("payday", s.paydayOf(cycle).toString());
        out.add(card(s, "T-07", CHILD, cycle, d, 2, "결제일 전 점검", body, extra));
    }

    // 25일: T-09 → T-10 → T-11 → T-17
    private void onPayday(SeedView s, LocalDate d, List<SeedView.Line> day, int settled, Out out) {
        String cycle = s.cycleOf(d);
        boolean settledToday = day.stream().anyMatch(l -> "SETTLEMENT".equals(l.type()));

        for (SeedView.Line l : day) {
            if (!"SHORTFALL".equals(l.type())) continue;
            out.add(card(s, "T-09", CHILD, cycle, d, 2, "부족분 발생",
                    "결제일에 " + won(l.amount()) + "이 모자랐어요. 다음 용돈 전까지 보내면 해소로 기록돼요. 다른 사람에게 빌리지 마세요.",
                    Map.of("shortfall", l.amount())));
            out.add(card(s, "T-09", PARENT, cycle, d, 2, "부족분 발생",
                    "이번 결제일 정산에서 부족분이 생겼어요. 다음 용돈 전까지 아이가 직접 보낼 수 있어요.",
                    Map.of()));

            Map<String, Object> prev = s.monthlyOf(SeedView.ym(d.minusMonths(1)));
            long prevShort = prev == null ? 0 : SeedView.num(prev.get("shortfall")).longValue();
            if (prevShort > 0) {
                String prevCycle = SeedView.str(prev.get("cycle"));
                out.add(card(s, "T-10", PARENT, cycle, d, 3, "용돈 계획 다시 정하기",
                        monthLabel(prevCycle) + "에 " + won(prevShort) + ", " + monthLabel(cycle) + "에 " + won(l.amount())
                                + "이 모자랐어요. 2개월 연속이라 용돈이나 쓰는 계획을 아이와 함께 다시 정해 보면 좋아요.",
                        Map.of("shortfalls", List.of(prevShort, l.amount()))));
                out.add(card(s, "T-10", CHILD, cycle, d, 3, "용돈 계획 다시 정하기",
                        "두 달 연속 모자라서, 부모님께 용돈 계획을 다시 이야기해 보자는 카드가 갔어요.", Map.of()));
            }
        }

        if (settledToday && s.ageOn(d) >= 18 && settled < REQUIRED_SETTLEMENTS) {
            int left = REQUIRED_SETTLEMENTS - settled;
            out.add(card(s, "T-11", CHILD, cycle, d, 5, "확인서 발급까지",
                    "확인서 발급까지 결제일 정산 " + left + "번 남았어요.",
                    Map.of("settled", settled, "left", left)));
        }
        if (settledToday && s.ageOn(d) >= 19 && settled >= REQUIRED_SETTLEMENTS) {
            certificateReady(s, d, settled, out);
        }
    }

    // 26일: T-12
    private void onDayAfterPayday(SeedView s, LocalDate d, Out out) {
        String cycle = SeedView.ym(d);
        Map<String, Object> m = s.monthlyOf(cycle);
        if (m == null || "진행 중".equals(SeedView.str(m.get("status")))) return;
        out.add(llmCard(s, "T-12", CHILD, cycle, d, 4, "월말 회고", reviewFacts(s, m, d)));
    }

    // 아무 날: T-13
    private void onRepay(SeedView s, LocalDate d, List<SeedView.Line> day, Out out) {
        for (SeedView.Line l : day) {
            if (!"SHORTFALL_REPAY".equals(l.type())) continue;
            long days = java.time.temporal.ChronoUnit.DAYS.between(s.paydayOf(l.forCycle()), d);
            Map<String, Object> extra = Map.of("amount", l.amount(), "days", days, "for_cycle", l.forCycle());
            out.add(card(s, "T-13", CHILD, l.forCycle(), d, 2, "부족분 해소",
                    "부족분 " + won(l.amount()) + "을 " + days + "일 만에 직접 보냈어요. '스스로 해소'로 기록됐어요.", extra));
            out.add(card(s, "T-13", PARENT, l.forCycle(), d, 2, "부족분 해소",
                    "아이가 부족분 " + won(l.amount()) + "을 " + days + "일 만에 직접 보냈어요.", extra));
        }
    }

    // 생일: T-16 → T-17
    private void onAdult(SeedView s, LocalDate d, int settled, Out out) {
        out.add(card(s, "T-16", CHILD, "once", d, 5, "성년 전환",
                "만 19세가 됐어요. 지금까지의 기록은 이제 본인만 볼 수 있어요.", Map.of()));
        out.add(card(s, "T-16", PARENT, "once", d, 5, "성년 전환",
                s.childName + "님이 만 19세가 됐어요. 이제 기록은 본인만 볼 수 있고, 함께한 결제일 횟수(" + settled + "번)만 보여요.",
                Map.of("settled", settled)));
        if (settled >= REQUIRED_SETTLEMENTS) certificateReady(s, d, settled, out);
    }

    private void certificateReady(SeedView s, LocalDate d, int settled, Out out) {
        out.add(card(s, "T-17", CHILD, "once", d, 5, "확인서 발급 가능",
                "금융행동 기록 확인서를 발급할 수 있어요. 결제일 정산 " + settled + "번의 기록이 담겨요.",
                Map.of("settled", settled)));
    }

    // ───────────────────────── LLM 카드에 넘길 사실 ─────────────────────────

    /** monthly_review.md 의 [사실 항목] */
    Map<String, Object> reviewFacts(SeedView s, Map<String, Object> m, LocalDate asOf) {
        String cycle = SeedView.str(m.get("cycle"));
        Map<String, Object> f = new LinkedHashMap<>();
        f.put("cycle", cycle);
        f.put("allowance", won(m.get("allowance")));
        f.put("mission_income", won(m.get("missions")));
        f.put("bill", won(m.get("bill")));
        f.put("result", resultAsOf(s, m, asOf));
        long shortfall = SeedView.num(m.get("shortfall")).longValue();
        if (shortfall > 0) f.put("shortfall", won(shortfall));
        f.put("streak", streakAt(s, cycle) + "개월");
        f.put("top_categories", topCategories(s, cycle, 2));
        Map<String, Object> prev = s.monthlyOf(SeedView.ym(LocalDate.parse(cycle + "-01").minusMonths(1)));
        if (prev != null) {
            long now = SeedView.num(m.get("bill")).longValue();
            long before = SeedView.num(prev.get("bill")).longValue();
            f.put("vs_last_month", now > before ? "늘었어요" : now < before ? "줄었어요" : "같아요");
        }
        return f;
    }

    /** monthly_briefing.md 의 [사실 항목]. 부모가 볼 수 있는 값만 넣어요 (사용처·항목 없음) */
    Map<String, Object> briefingFacts(SeedView s, Map<String, Object> m, LocalDate asOf) {
        String cycle = SeedView.str(m.get("cycle"));
        long missionCount = s.ledger.stream()
                .filter(l -> "MISSION_REWARD".equals(l.type()) && cycle.equals(l.cycle())).count();
        Map<String, Object> f = new LinkedHashMap<>();
        f.put("child_name", s.childName);
        f.put("cycle", cycle);
        f.put("allowance", won(m.get("allowance")));
        f.put("mission_income", won(m.get("missions")) + (missionCount > 0 ? " (미션 " + missionCount + "개)" : ""));
        f.put("bill_total", won(m.get("bill")));
        f.put("result", resultAsOf(s, m, asOf));
        f.put("streak", streakAt(s, cycle) + "개월");
        f.put("record_count", settledUntil(s, s.paydayOf(cycle)) + "번");
        return f;
    }

    /** decision_card.md 의 [사실 항목]. 근거는 원장에서 계산할 수 있는 것만 넣어요 */
    Map<String, Object> decisionFacts(SeedView s, LocalDate d, Map<String, Object> proposal) {
        List<Map<String, Object>> done = s.monthly.stream()
                .filter(m -> !"진행 중".equals(SeedView.str(m.get("status"))))
                .filter(m -> s.paydayOf(SeedView.str(m.get("cycle"))).isBefore(d))
                .toList();
        List<Map<String, Object>> last6 = done.subList(Math.max(0, done.size() - 6), done.size());

        long avgBill = Math.round(last6.stream().mapToLong(m -> SeedView.num(m.get("bill")).longValue()).average().orElse(0) / 100.0) * 100;
        long avgKeep = Math.round(last6.stream().mapToDouble(m -> SeedView.num(m.get("keep_ratio")).doubleValue()).average().orElse(0) * 100);
        long shortMonths = last6.stream().filter(m -> SeedView.num(m.get("shortfall")).longValue() > 0).count();

        LocalDate from = LocalDate.parse(SeedView.str(proposal.get("from")) + "-01");
        Map<String, Object> f = new LinkedHashMap<>();
        f.put("child_name", s.childName);
        f.put("current_allowance", won(currentAllowance(s, d)));
        f.put("proposed_allowance", won(proposal.get("amount")));
        f.put("apply_from", from.getMonthValue() + "월 1일");
        f.put("reasons", List.of(
                List.of("최근 6개월 평균 청구액", won(avgBill)),
                List.of("최근 6개월 평균 남긴 비율", avgKeep + "%"),
                List.of("최근 6개월 중 부족했던 달", shortMonths + "번")));
        return f;
    }

    // ───────────────────────── 계산 도우미 ─────────────────────────

    /** 그날 기준 결과. 원장의 resolution은 나중 일까지 알고 있어서, 그날까지의 기록으로 다시 판단해요 */
    String resultAsOf(SeedView s, Map<String, Object> m, LocalDate asOf) {
        if ("완납".equals(SeedView.str(m.get("status")))) return "완납";
        String cycle = SeedView.str(m.get("cycle"));
        boolean repaid = s.ledger.stream().anyMatch(l -> "SHORTFALL_REPAY".equals(l.type())
                && cycle.equals(l.forCycle()) && !l.date().isAfter(asOf));
        return repaid ? "스스로 해소" : "자동 정산 예정";
    }

    int streakAt(SeedView s, String cycle) {
        int streak = 0;
        for (Map<String, Object> m : s.monthly) {
            if ("진행 중".equals(SeedView.str(m.get("status")))) break;
            streak = "완납".equals(SeedView.str(m.get("status"))) ? streak + 1 : 0;
            if (cycle.equals(SeedView.str(m.get("cycle")))) return streak;
        }
        return streak;
    }

    int settledUntil(SeedView s, LocalDate d) {
        return (int) s.ledger.stream().filter(l -> "SETTLEMENT".equals(l.type()) && !l.date().isAfter(d)).count();
    }

    long billUntil(SeedView s, String cycle, LocalDate d) {
        long sum = 0;
        for (SeedView.Line l : s.ledger) {
            if (!cycle.equals(l.cycle()) || l.date().isAfter(d)) continue;
            if ("CARD_PAYMENT".equals(l.type())) sum += l.amount();
            if ("CARD_CANCEL".equals(l.type())) sum -= l.amount();
        }
        return sum;
    }

    List<List<String>> topCategories(SeedView s, String cycle, int n) {
        Map<String, Long> by = new LinkedHashMap<>();
        for (SeedView.Line l : s.ledger) {
            if (!cycle.equals(l.cycle()) || l.category().isEmpty()) continue;
            if ("CARD_PAYMENT".equals(l.type())) by.merge(l.category(), l.amount(), Long::sum);
            if ("CARD_CANCEL".equals(l.type())) by.merge(l.category(), -l.amount(), Long::sum);
        }
        return by.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(n)
                .map(e -> List.of(e.getKey(), won(e.getValue())))
                .toList();
    }

    long currentAllowance(SeedView s, LocalDate d) {
        long amount = 0;
        for (Map<String, Object> a : s.allowanceHistory) {
            if (LocalDate.parse(SeedView.str(a.get("from")) + "-01").isAfter(d)) break;
            amount = SeedView.num(a.get("amount")).longValue();
        }
        return amount;
    }

    /**
     * 앞으로 3개월 안에 바뀔 용돈 중 AI 제안으로 정해진 것 (seed.json allowance_history의 reason에 "AI"가 있으면).
     * 물가 데이터가 seed.json에 없어서, 제안 금액은 원장 생성기에 적어 둔 값을 써요.
     */
    Map<String, Object> upcomingProposal(SeedView s, LocalDate d) {
        for (Map<String, Object> a : s.allowanceHistory) {
            LocalDate from = LocalDate.parse(SeedView.str(a.get("from")) + "-01");
            if (from.isAfter(d) && !from.isAfter(d.plusMonths(3)) && SeedView.str(a.get("reason")).contains("AI")) return a;
        }
        return null;
    }

    static String won(Object amount) {
        return NumberFormat.getNumberInstance(Locale.KOREA).format(SeedView.num(amount).longValue()) + "원";
    }

    static String monthLabel(String cycle) {
        return Integer.parseInt(cycle.substring(5)) + "월";
    }

    private static String s(Object o) {
        return o == null ? "" : o.toString();
    }

    private TriggerCard card(SeedView s, String trigger, String audience, String period, LocalDate d, int group,
                             String title, String body, Map<String, Object> extra) {
        return new TriggerCard(trigger, s.childId, audience, period, d.toString(), group, title, body, extra, "틀", Map.of());
    }

    private TriggerCard llmCard(SeedView s, String trigger, String audience, String period, LocalDate d, int group,
                                String title, Map<String, Object> facts) {
        return new TriggerCard(trigger, s.childId, audience, period, d.toString(), group, title, null, Map.of(), "LLM 대기", facts);
    }

    /** 같은 (아이, 트리거, 받는 사람, 기간)이면 한 번만 */
    private static final class Out {
        final List<TriggerCard> cards = new ArrayList<>();
        final Set<String> keys = new LinkedHashSet<>();

        void add(TriggerCard c) {
            if (keys.add(c.key())) cards.add(c);
        }
    }
}
