package com.cheotdanchu.service;

import com.cheotdanchu.ai.SeedRepository;
import com.cheotdanchu.common.exception.GeneralException;
import com.cheotdanchu.common.response.ErrorStatus;
import com.cheotdanchu.dto.PaymentDtos;
import com.cheotdanchu.entity.Member;
import com.cheotdanchu.entity.Settlement;
import com.cheotdanchu.repository.CardPaymentRepository;
import com.cheotdanchu.repository.MemberRepository;
import com.cheotdanchu.repository.SettlementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** 결제 내역 화면(자녀 전용) */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChildPaymentService {

    private final MemberRepository memberRepository;
    private final SettlementRepository settlementRepository;
    private final CardPaymentRepository cardPaymentRepository;
    private final SeedRepository seedRepository;

    /**
     * 선택한 기간(기본: 이번 정산 기간 전체)의 카테고리별 합계와 비중. 월말 회고·부모 브리핑이 같은 숫자를 쓴다.
     * 취소(마이너스)를 합산한 값이라 전체 합이 청구 예정액과 같고, 합계가 0 이하가 된 카테고리는 빼고 센다.
     * 진행 중인 정산이 없으면(성년 전환 후 등) 에러가 아니라 빈 목록이다.
     */
    public PaymentDtos.CategoryRatioResponse getCategoryRatio(Long personaId, LocalDate from, LocalDate to) {
        Member child = memberRepository.getChild(personaId);
        Optional<Settlement> current = settlementRepository.findCurrent(child.getFamily().getFamilyId(), seedRepository.today());
        if (current.isEmpty()) return new PaymentDtos.CategoryRatioResponse(0, List.of());

        Settlement settlement = current.get();
        LocalDate start = from == null ? settlement.getPeriodStart() : from;
        LocalDate end = to == null ? settlement.getPeriodEnd() : to;
        validatePeriod(settlement, start, end);

        List<CardPaymentRepository.CategoryTotal> totals = cardPaymentRepository
                .sumByCategory(settlement.getSettlementId(), child.getMemberId(), start.atStartOfDay(), end.plusDays(1).atStartOfDay())
                .stream()
                .filter(t -> t.getAmount() != null && t.getAmount() > 0)
                .sorted(Comparator.comparing(CardPaymentRepository.CategoryTotal::getAmount).reversed()
                        .thenComparing(CardPaymentRepository.CategoryTotal::getCategory))
                .toList();
        long total = totals.stream().mapToLong(CardPaymentRepository.CategoryTotal::getAmount).sum();

        List<PaymentDtos.CategoryRatio> items = totals.stream()
                .map(t -> new PaymentDtos.CategoryRatio(t.getCategory(), t.getAmount(), ratio(t.getAmount(), total)))
                .toList();
        return new PaymentDtos.CategoryRatioResponse(total, items);
    }

    /** 고른 날짜는 정산 기간 안이어야 하고, 끝나는 날이 시작일보다 빠르면 안 된다 */
    private static void validatePeriod(Settlement settlement, LocalDate from, LocalDate to) {
        if (to.isBefore(from)) throw new GeneralException(ErrorStatus.INVALID_PERIOD);
        if (from.isBefore(settlement.getPeriodStart()) || to.isAfter(settlement.getPeriodEnd())) {
            throw new GeneralException(ErrorStatus.INVALID_INPUT, String.format("정산 기간(%s~%s) 안에서 골라주세요",
                    settlement.getPeriodStart(), settlement.getPeriodEnd()));
        }
    }

    /** 전체 대비 비중(%), 소수 첫째 자리에서 반올림 */
    private static double ratio(long amount, long total) {
        return BigDecimal.valueOf(amount * 100).divide(BigDecimal.valueOf(total), 1, RoundingMode.HALF_UP).doubleValue();
    }
}
