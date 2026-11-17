package com.cheotdanchu.service;

import com.cheotdanchu.ai.SeedRepository;
import com.cheotdanchu.common.exception.GeneralException;
import com.cheotdanchu.common.response.ErrorStatus;
import com.cheotdanchu.dto.CurrentSettlementResponse;
import com.cheotdanchu.dto.PaymentDtos;
import com.cheotdanchu.entity.CardPayment;
import com.cheotdanchu.entity.Member;
import com.cheotdanchu.entity.Settlement;
import com.cheotdanchu.repository.CardPaymentRepository;
import com.cheotdanchu.repository.MemberRepository;
import com.cheotdanchu.repository.SettlementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/** 결제 내역 화면(자녀 전용) */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChildPaymentService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;
    /** 첫 페이지는 커서를 이 먼 미래 시각으로 둬서 처음부터 읽는다 */
    private static final LocalDateTime FAR_FUTURE = LocalDateTime.of(9999, 12, 31, 0, 0);
    private static final String DATE_KIND = "d";
    private static final String AMOUNT_KIND = "a";

    private final MemberRepository memberRepository;
    private final SettlementRepository settlementRepository;
    private final CardPaymentRepository cardPaymentRepository;
    private final SeedRepository seedRepository;
    private final SettlementService settlementService;

    /** 지금 보는 아이, 이번 정산, 고른 기간(from 이상 to 이하의 날짜). 기간을 안 고르면 정산 기간 전체 */
    private record View(Member child, Settlement settlement, LocalDate from, LocalDate to) {

        LocalDateTime fromAt() {
            return from.atStartOfDay();
        }

        /** 끝나는 날의 마지막까지 포함하려고 다음 날 0시 미만으로 자른다 */
        LocalDateTime toBefore() {
            return to.plusDays(1).atStartOfDay();
        }
    }

    /**
     * 이번 정산과 고른 기간을 확인한다(자녀 확인은 호출한 쪽에서 한다). 진행 중인 정산이 없으면(성년 전환 후 등) empty.
     * 고른 날짜는 정산 기간 안이어야 하고, 끝나는 날이 시작일보다 빠르면 안 된다.
     */
    private Optional<View> view(Member child, LocalDate from, LocalDate to) {
        Optional<Settlement> current = settlementRepository.findCurrent(child.getFamily().getFamilyId(), seedRepository.today());
        if (current.isEmpty()) return Optional.empty();

        Settlement settlement = current.get();
        LocalDate start = from == null ? settlement.getPeriodStart() : from;
        LocalDate end = to == null ? settlement.getPeriodEnd() : to;
        if (end.isBefore(start)) throw new GeneralException(ErrorStatus.INVALID_PERIOD);
        if (start.isBefore(settlement.getPeriodStart()) || end.isAfter(settlement.getPeriodEnd())) {
            throw new GeneralException(ErrorStatus.INVALID_INPUT, String.format("정산 기간(%s~%s) 안에서 골라주세요",
                    settlement.getPeriodStart(), settlement.getPeriodEnd()));
        }
        return Optional.of(new View(child, settlement, start, end));
    }

    /**
     * 선택한 기간(기본: 이번 정산 기간 전체)의 카테고리별 합계와 비중. 월말 회고·부모 브리핑이 같은 숫자를 쓴다.
     * 취소(마이너스)를 합산한 값이라 전체 합이 청구 예정액과 같고, 합계가 0 이하가 된 카테고리는 빼고 센다.
     * 진행 중인 정산이 없으면 에러가 아니라 빈 목록이다.
     */
    public PaymentDtos.CategoryRatioResponse getCategoryRatio(Long personaId, LocalDate from, LocalDate to) {
        Optional<View> view = view(memberRepository.getChild(personaId), from, to);
        if (view.isEmpty()) return new PaymentDtos.CategoryRatioResponse(0, List.of());
        View v = view.get();

        List<CardPaymentRepository.CategoryTotal> totals = cardPaymentRepository
                .sumByCategory(v.settlement().getSettlementId(), v.child().getMemberId(), v.fromAt(), v.toBefore())
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

    /**
     * 결제 요약: 이번 정산의 청구 예정액(취소 합산)과 결제 건수(취소 제외), 결제일. 진행 중인 정산이 없으면 null.
     * 자녀 홈의 현재 정산 조회와 같은 숫자여야 해서 그 계산을 그대로 쓴다 (자녀 확인도 거기서 한다).
     */
    public PaymentDtos.SummaryResponse getSummary(Long personaId) {
        CurrentSettlementResponse current = settlementService.getCurrent(personaId);
        if (current == null) return null;

        return new PaymentDtos.SummaryResponse(
                current.settlementId(),
                current.periodStart(),
                current.periodEnd(),
                current.billedAmount(),
                current.paymentCount(),
                current.dueAt().toLocalDate());
    }

    /**
     * 결제 목록. sort 는 date(날짜순, 기본) 또는 amount(큰 금액순), size 는 기본 20(최대 100).
     * 다음 페이지는 응답의 nextCursor 를 그대로 보내서 읽는다. 진행 중인 정산이 없으면 에러가 아니라 빈 목록이다.
     */
    public PaymentDtos.PaymentListResponse getPayments(Long personaId, String sort, LocalDate from, LocalDate to, Integer size, String cursor) {
        Member child = memberRepository.getChild(personaId); // 부모면 입력값 오류보다 먼저 403
        boolean byAmount = isByAmount(sort);
        int pageSize = size == null ? DEFAULT_PAGE_SIZE : size;
        if (pageSize < 1 || pageSize > MAX_PAGE_SIZE) throw new GeneralException(ErrorStatus.INVALID_INPUT);

        Optional<View> view = view(child, from, to);
        if (view.isEmpty()) return new PaymentDtos.PaymentListResponse(List.of(), 0, null);
        View v = view.get();
        Long settlementId = v.settlement().getSettlementId();
        Long memberId = v.child().getMemberId();
        // 다음 페이지가 있는지 알려고 한 건을 더 읽는다
        PageRequest firstPlusOne = PageRequest.of(0, pageSize + 1);
        List<CardPayment> page = byAmount ? readByAmount(v, cursor, firstPlusOne) : readByDate(v, cursor, firstPlusOne);

        boolean hasNext = page.size() > pageSize;
        List<CardPayment> shown = hasNext ? page.subList(0, pageSize) : page;
        List<PaymentDtos.PaymentItem> items = shown.stream()
                .map(p -> new PaymentDtos.PaymentItem(p.getPaymentId(), p.getPaidAt(), p.getMerchantName(), p.getCategory(),
                        p.getAmount(), p.isCanceled(), p.isHighAmount()))
                .toList();
        long totalCount = cardPaymentRepository.countBySettlementIdAndMemberIdAndPaidAtGreaterThanEqualAndPaidAtLessThan(
                settlementId, memberId, v.fromAt(), v.toBefore());
        return new PaymentDtos.PaymentListResponse(items, totalCount, hasNext ? encodeCursor(byAmount, shown.get(shown.size() - 1)) : null);
    }

    /** 큰 금액순 한 페이지. 커서가 없으면 맨 앞부터 */
    private List<CardPayment> readByAmount(View v, String cursor, PageRequest pageable) {
        Cursor<Integer> from = cursor == null ? new Cursor<>(Integer.MAX_VALUE, Long.MAX_VALUE)
                : decodeCursor(cursor, AMOUNT_KIND, Integer::parseInt);
        return cardPaymentRepository.pageByAmount(v.settlement().getSettlementId(), v.child().getMemberId(),
                v.fromAt(), v.toBefore(), from.key(), from.id(), pageable);
    }

    /** 날짜순(최신 먼저) 한 페이지. 커서가 없으면 맨 앞부터 */
    private List<CardPayment> readByDate(View v, String cursor, PageRequest pageable) {
        Cursor<LocalDateTime> from = cursor == null ? new Cursor<>(FAR_FUTURE, Long.MAX_VALUE)
                : decodeCursor(cursor, DATE_KIND, LocalDateTime::parse);
        return cardPaymentRepository.pageByDate(v.settlement().getSettlementId(), v.child().getMemberId(),
                v.fromAt(), v.toBefore(), from.key(), from.id(), pageable);
    }

    private static boolean isByAmount(String sort) {
        if (sort == null || "date".equals(sort)) return false;
        if ("amount".equals(sort)) return true;
        throw new GeneralException(ErrorStatus.INVALID_INPUT);
    }

    /** 다음 페이지 커서. 정렬 종류(d/a), 그 정렬의 기준 값, 결제 ID 를 이어 붙여 URL 에 안전하게 인코딩한다 */
    private static String encodeCursor(boolean byAmount, CardPayment last) {
        String raw = byAmount ? AMOUNT_KIND + "|" + last.getAmount() + "|" + last.getPaymentId()
                : DATE_KIND + "|" + last.getPaidAt() + "|" + last.getPaymentId();
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    /** 풀어낸 커서: 그 정렬의 기준 값과 결제 ID */
    private record Cursor<K>(K key, long id) {}

    /** 커서를 풀어 기준 값(keyParser 로 변환)과 결제 ID 를 돌려준다. 깨졌거나 다른 정렬에서 받았거나 값이 숫자·시각이 아니면 400 */
    private static <K> Cursor<K> decodeCursor(String cursor, String expectedKind, Function<String, K> keyParser) {
        try {
            String[] parts = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8).split("\\|", 3);
            if (parts.length == 3 && parts[0].equals(expectedKind)) return new Cursor<>(keyParser.apply(parts[1]), Long.parseLong(parts[2]));
        } catch (RuntimeException ignored) {
            // 디코딩·숫자·시각 변환 실패는 모두 아래에서 400
        }
        throw new GeneralException(ErrorStatus.INVALID_INPUT);
    }

    /** 전체 대비 비중(%), 소수 첫째 자리에서 반올림 */
    private static double ratio(long amount, long total) {
        return BigDecimal.valueOf(amount * 100).divide(BigDecimal.valueOf(total), 1, RoundingMode.HALF_UP).doubleValue();
    }
}
