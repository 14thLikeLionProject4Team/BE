package com.cheotdanchu.service;

import com.cheotdanchu.ai.SeedRepository;
import com.cheotdanchu.dto.ChildHomeDtos;
import com.cheotdanchu.dto.CurrentSettlementResponse;
import com.cheotdanchu.entity.FamilySetting;
import com.cheotdanchu.entity.Member;
import com.cheotdanchu.entity.Settlement;
import com.cheotdanchu.repository.AllowanceChangeRepository;
import com.cheotdanchu.repository.CardPaymentRepository;
import com.cheotdanchu.repository.FamilySettingRepository;
import com.cheotdanchu.repository.MemberRepository;
import com.cheotdanchu.repository.SettlementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** 자녀 홈 화면(자녀 전용) */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChildHomeService {

    private final MemberRepository memberRepository;
    private final SettlementRepository settlementRepository;
    private final CardPaymentRepository cardPaymentRepository;
    private final FamilySettingRepository familySettingRepository;
    private final AllowanceChangeRepository allowanceChangeRepository;
    private final SeedRepository seedRepository;
    private final SettlementService settlementService;
    private final RecordsService recordsService;
    private final CertificateEligibilityService certificateEligibilityService;

    /**
     * 홈 화면에 필요한 값을 한 번에: 상태 칩, 이번 정산, 부족 예상 점검 카드를 보일지.
     * 정산과 점검은 현재 정산 조회·부족 예상 조회와 같은 계산을 그대로 쓴다 (자녀 확인도 거기서 한다).
     */
    public ChildHomeDtos.HomeResponse getHome(Long personaId) {
        CurrentSettlementResponse settlement = settlementService.getCurrent(personaId);
        boolean shortfallCheckShow = settlementService.getCheck(personaId).visible();

        Member child = memberRepository.getChild(personaId);
        Long familyId = child.getFamily().getFamilyId();
        LocalDate today = seedRepository.today();

        LocalDate certificateFrom = certificateEligibilityService.availableFrom(child, today);
        FamilySetting.Visibility visibility = familySettingRepository.visibilityOf(familyId);

        return new ChildHomeDtos.HomeResponse(
                today,
                new ChildHomeDtos.Member(child.getMemberId(), child.getName()),
                new ChildHomeDtos.Chips(recordsService.currentStreak(familyId),
                        certificateFrom == null ? null : ChronoUnit.DAYS.between(today, certificateFrom), visibility),
                settlement,
                shortfallCheckShow);
    }

    /**
     * 이번 정산 기간 시작일부터 오늘까지 하루씩 사용액(취소는 합산)을 준다. 오늘 이후는 없고, 결제가 없는 날은 0이다.
     * 진행 중인 정산이 없으면 null.
     */
    public ChildHomeDtos.DailySpendingResponse getDailySpending(Long personaId) {
        Member child = memberRepository.getChild(personaId);
        Long familyId = child.getFamily().getFamilyId();
        LocalDate today = seedRepository.today();

        Optional<Settlement> current = settlementRepository.findCurrent(familyId, today);
        if (current.isEmpty()) return null;
        Settlement settlement = current.get();

        Map<LocalDate, Long> byDay = new HashMap<>();
        cardPaymentRepository.sumByDay(settlement.getSettlementId(), child.getMemberId(),
                        settlement.getPeriodStart().atStartOfDay(), today.plusDays(1).atStartOfDay())
                .forEach(t -> byDay.put(t.getDay(), t.getAmount()));

        List<ChildHomeDtos.DailyItem> items = new ArrayList<>();
        for (LocalDate day = settlement.getPeriodStart(); !day.isAfter(today); day = day.plusDays(1)) {
            items.add(new ChildHomeDtos.DailyItem(day, byDay.getOrDefault(day, 0L)));
        }
        // 이번 달 용돈: 부모 홈 카드(ChildSpendingService)와 같은 기준 = 이번 달 용돈 보내는 날에 적용 중인 금액
        Long allowance = familySettingRepository.findById(familyId)
                .flatMap(setting -> allowanceChangeRepository.findForMonth(familyId, YearMonth.from(today), setting.getAllowanceDay()))
                .map(change -> (long) change.getAmount())
                .orElse(null);
        return new ChildHomeDtos.DailySpendingResponse(settlement.getPeriodStart(), settlement.getPeriodEnd(), today, allowance, items);
    }
}
