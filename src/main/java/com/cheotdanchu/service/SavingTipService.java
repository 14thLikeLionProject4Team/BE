package com.cheotdanchu.service;

import com.cheotdanchu.ai.SeedRepository;
import com.cheotdanchu.dto.MissionQueryDtos;
import com.cheotdanchu.entity.Member;
import com.cheotdanchu.entity.Settlement;
import com.cheotdanchu.repository.CardPaymentRepository;
import com.cheotdanchu.repository.MemberRepository;
import com.cheotdanchu.repository.SettlementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

/**
 * 미션(자녀) 화면의 절약 팁 한 줄. 이번 정산 기간 결제에서 가장 많이 쓴 항목을 골라,
 * 부족 예상 카드가 떠 있으면 "몇 번 줄이면 부족분이 채워지는지", 아니면 "한 번 줄이면 얼마 아끼는지" 알려준다.
 * 누르면 결제 내역의 그 항목(금액순)으로 이동하도록 필터 값을 함께 준다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SavingTipService {

    private static final String[] COUNT_WORDS = {"", "한", "두", "세", "네", "다섯"};

    private final MemberRepository memberRepository;
    private final SettlementRepository settlementRepository;
    private final CardPaymentRepository cardPaymentRepository;
    private final SettlementService settlementService;
    private final SeedRepository seedRepository;

    /** 진행 중인 정산이 없거나 이번 기간 결제가 없으면 null */
    public MissionQueryDtos.SavingTip getSavingTip(Long personaId) {
        Member child = memberRepository.getChild(personaId);
        LocalDate today = seedRepository.today();
        Settlement current = settlementRepository.findCurrent(child.getFamily().getFamilyId(), today).orElse(null);
        if (current == null) return null;

        LocalDateTime from = current.getPeriodStart().atStartOfDay();
        LocalDateTime to = current.getPeriodEnd().plusDays(1).atStartOfDay();
        List<CardPaymentRepository.CategoryTotal> totals = cardPaymentRepository.sumByCategory(
                current.getSettlementId(), child.getMemberId(), from, to);
        if (totals.isEmpty()) return null;

        CardPaymentRepository.CategoryTotal top = totals.get(0);
        // 부분 취소(10,000 / -3,000)면 합계는 남아도 순건수가 0이 될 수 있어서 최소 1회, 최소 1원으로 둔다
        long average = Math.max(top.getAmount() / Math.max(top.getCount(), 1), 1);
        // 부족 예상 카드(결제일 D-6부터)가 보일 때만 부족분을 말한다. 카드와 같은 금액을 써서 두 화면의 숫자가 같다
        long shortfall = settlementService.getCheck(personaId).shortfallAmount();

        String text = shortfall > 0
                ? String.format(Locale.KOREA, "%s %s만 줄이면 부족분이 채워져요.", top.getCategory(), countWord(Math.ceilDiv(shortfall, average)))
                : String.format(Locale.KOREA, "%s에 가장 많이 썼어요. 한 번만 줄여도 %,d원을 아낄 수 있어요.", top.getCategory(), average);
        return new MissionQueryDtos.SavingTip(text, new MissionQueryDtos.SavingTipLink(top.getCategory(), "amount"));
    }

    /** 횟수 읽기: 1~5는 "한 번·두 번…다섯 번", 그보다 크면 "7번" */
    private static String countWord(long n) {
        return n >= 1 && n < COUNT_WORDS.length ? COUNT_WORDS[(int) n] + " 번" : n + "번";
    }
}
