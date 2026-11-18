package com.cheotdanchu.ai;

import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 원장 생성기의 seed.json 한 가족분을 읽기 쉽게 감싼 것.
 * 주머니 이름은 accounts.pockets 에서 그대로 읽어요. (0번 = 이번 달 정산에 쓰는 주머니, 1번 = 남은 돈이 옮겨지는 주머니)
 */
public final class SeedView {

    /** 원장 한 줄 */
    public record Line(String id, LocalDate date, String time, String type, long amount,
                       String from, String to, String category, String cycle, String forCycle) {}

    public final String family;
    public final String childId;
    public final String childName;
    public final String parentId;
    public final String parentName;
    public final LocalDate birth;
    public final LocalDate demoToday;
    public final String pocketNow;
    public final String pocketLeft;
    public final int allowanceDay;
    public final int payday;
    public final List<Line> ledger;
    public final List<Map<String, Object>> monthly;
    public final List<Map<String, Object>> allowanceHistory;
    public final Map<String, Object> reference;

    @SuppressWarnings("unchecked")
    public SeedView(Map<String, Object> seed) {
        Map<String, Object> child = (Map<String, Object>) seed.get("child");
        Map<String, Object> parent = (Map<String, Object>) seed.get("parent");
        Map<String, Object> accounts = (Map<String, Object>) seed.get("accounts");
        Map<String, Object> rules = (Map<String, Object>) seed.get("rules");
        List<Object> pockets = (List<Object>) accounts.get("pockets");

        this.family = str(seed.get("family"));
        this.childId = str(child.get("id"));
        this.childName = str(child.get("name"));
        this.parentId = str(parent.get("id"));
        this.parentName = str(parent.get("name"));
        this.birth = LocalDate.parse(str(child.get("birth")));
        this.demoToday = LocalDate.parse(str(seed.get("demo_today")));
        this.pocketNow = str(pockets.get(0));
        this.pocketLeft = str(pockets.get(1));
        this.allowanceDay = num(rules.get("allowance_day")).intValue();
        this.payday = num(rules.get("payday")).intValue();
        this.reference = (Map<String, Object>) seed.get("reference");
        this.monthly = (List<Map<String, Object>>) reference.get("monthly");
        this.allowanceHistory = (List<Map<String, Object>>) seed.get("allowance_history");

        List<Line> lines = new ArrayList<>();
        for (Object o : (List<Object>) seed.get("ledger")) {
            Map<String, Object> m = (Map<String, Object>) o;
            lines.add(new Line(str(m.get("id")), LocalDate.parse(str(m.get("date"))), str(m.get("time")),
                    str(m.get("type")), num(m.get("amount")).longValue(), str(m.get("from")), str(m.get("to")),
                    str(m.get("category")), str(m.get("cycle")), str(m.get("for_cycle"))));
        }
        this.ledger = List.copyOf(lines);
    }

    public int ageOn(LocalDate d) {
        return Period.between(birth, d).getYears();
    }

    /** 정산 달: 25일까지는 그달, 26일부터는 다음 달 */
    public String cycleOf(LocalDate d) {
        LocalDate base = d.getDayOfMonth() <= payday ? d : d.plusMonths(1);
        return ym(base);
    }

    public LocalDate paydayOf(String cycle) {
        return LocalDate.parse(cycle + "-01").withDayOfMonth(payday);
    }

    public Map<String, Object> monthlyOf(String cycle) {
        for (Map<String, Object> m : monthly) if (cycle.equals(str(m.get("cycle")))) return m;
        return null;
    }

    static String ym(LocalDate d) {
        return String.format("%04d-%02d", d.getYear(), d.getMonthValue());
    }

    static String str(Object o) {
        return o == null ? "" : o.toString();
    }

    static Number num(Object o) {
        if (o instanceof Number n) return n;
        if (o == null || o.toString().isBlank()) return 0;
        return Double.parseDouble(o.toString());
    }
}
