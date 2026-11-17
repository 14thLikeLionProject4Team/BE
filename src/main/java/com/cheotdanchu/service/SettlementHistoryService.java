package com.cheotdanchu.service;

import com.cheotdanchu.common.exception.GeneralException;
import com.cheotdanchu.common.response.ErrorStatus;
import com.cheotdanchu.dto.SettlementHistoryDtos;
import com.cheotdanchu.entity.Member;
import com.cheotdanchu.entity.Settlement;
import com.cheotdanchu.entity.Transfer;
import com.cheotdanchu.repository.CardPaymentRepository;
import com.cheotdanchu.repository.MemberRepository;
import com.cheotdanchu.repository.SettlementRepository;
import com.cheotdanchu.repository.TransferRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/** 마이(자녀)의 결제일 기록과 결제 내역의 월별 상세 (자녀 전용) */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SettlementHistoryService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;
    /** 아직 정산 전인 이번 달. 결제일 기록에는 넣지 않는다 */
    private static final Settlement.Status NOT_SETTLED_YET = Settlement.Status.SCHEDULED;
    /** 첫 페이지는 커서를 이 먼 미래 날짜로 둬서 처음부터 읽는다 */
    private static final LocalDate FAR_FUTURE = LocalDate.of(9999, 12, 31);

    /** 결제일을 지나 부족 여부가 정해진 정산 (정산 전이거나 결제일 당일 처리 전인 SCHEDULED·REQUIRED 는 부족액을 말할 수 없다). 아직 해결 못 한 SHORTFALL·PARTIAL 도 들어 있다 */
    private static final Set<Settlement.Status> SHORTFALL_KNOWN_STATUSES =
            EnumSet.of(Settlement.Status.SHORTFALL, Settlement.Status.PARTIAL, Settlement.Status.COMPLETED);

    private final MemberRepository memberRepository;
    private final SettlementRepository settlementRepository;
    private final CardPaymentRepository cardPaymentRepository;
    private final TransferRepository transferRepository;

    /**
     * 월별 결제일 결과를 최신순으로. 정산 전(SCHEDULED)인 이번 달은 아직 결과가 없어서 넣지 않는다.
     * size 는 기본 20(최대 100), 다음 페이지는 응답의 nextCursor 를 그대로 보낸다.
     */
    public SettlementHistoryDtos.ListResponse getHistory(Long personaId, Integer size, String cursor) {
        Member child = memberRepository.getChild(personaId);
        int pageSize = size == null ? DEFAULT_PAGE_SIZE : size;
        if (pageSize < 1 || pageSize > MAX_PAGE_SIZE) throw new GeneralException(ErrorStatus.INVALID_INPUT);
        Long familyId = child.getFamily().getFamilyId();

        LocalDate before = cursor == null ? FAR_FUTURE : decodeCursor(cursor);
        // 다음 페이지가 있는지 알려고 한 건을 더 읽는다
        List<Settlement> page = settlementRepository.findByFamilyIdAndStatusNotAndPeriodEndLessThanOrderByPeriodEndDesc(
                familyId, NOT_SETTLED_YET, before, PageRequest.of(0, pageSize + 1));
        boolean hasNext = page.size() > pageSize;
        List<Settlement> shown = hasNext ? page.subList(0, pageSize) : page;

        List<SettlementHistoryDtos.Item> items = shown.stream()
                .map(s -> new SettlementHistoryDtos.Item(s.getSettlementId(), s.getPeriodStart(), s.getPeriodEnd(),
                        s.getStatus(), s.getResult(), s.getBilledAmount(), s.isExcluded()))
                .toList();
        return new SettlementHistoryDtos.ListResponse(items,
                settlementRepository.countByFamilyIdAndStatusNot(familyId, NOT_SETTLED_YET),
                hasNext ? encodeCursor(shown.get(shown.size() - 1).getPeriodEnd()) : null);
    }

    /** 한 달치 정산의 금액·결과와 그 달 정산에 연결된 이체 내역. 남의 정산이면 404 */
    public SettlementHistoryDtos.Detail getDetail(Long personaId, Long settlementId) {
        Member child = memberRepository.getChild(personaId);
        Settlement s = settlementRepository.findById(settlementId)
                .filter(found -> found.getFamilyId().equals(child.getFamily().getFamilyId()))
                .orElseThrow(() -> new GeneralException(ErrorStatus.SETTLEMENT_NOT_FOUND));

        // 이번 달처럼 아직 청구액이 확정되지 않은 정산은 카드 결제 합계로 보여준다
        long billed = cardPaymentRepository.billedOf(s);
        List<Transfer> transfers = transferRepository.findBySettlementIdOrderByTransferredAtAscTransferIdAsc(settlementId);
        // 결제일에 이체된 금액과 그때의 부족액은 결제일 정산(SETTLEMENT) 이체에서 구한다. settlements.paid_amount 는 부족분을 갚으면
        // 바뀔 수 있어서 쓰지 않는다 (쓰면 부족분을 갚은 달이 "청구액 전부 이체, 부족 0"으로 보이고 이체 내역과 어긋난다)
        long paidAtDue = sumOf(transfers, Transfer.Type.SETTLEMENT);
        long shortfall = SHORTFALL_KNOWN_STATUSES.contains(s.getStatus()) ? Math.max(billed - paidAtDue, 0) : 0;
        long repaid = sumOf(transfers, Transfer.Type.SHORTFALL_PAYMENT);

        List<SettlementHistoryDtos.TransferItem> items = transfers.stream()
                .map(t -> new SettlementHistoryDtos.TransferItem(t.getType(), t.getSource(), t.getAmount(), t.getTransferredAt()))
                .toList();
        return new SettlementHistoryDtos.Detail(s.getSettlementId(), s.getPeriodStart(), s.getPeriodEnd(), s.getStatus(), s.getResult(),
                billed, (int) paidAtDue, shortfall, s.remainingShortfall(billed, paidAtDue, repaid), s.isExcluded(), s.getResolvedAt(), items);
    }

    /** 이체 목록에서 한 종류의 금액 합 */
    private static long sumOf(List<Transfer> transfers, Transfer.Type type) {
        return transfers.stream().filter(t -> t.getType() == type).mapToLong(Transfer::getAmount).sum();
    }

    /** 정산 종료일은 가족마다 하나씩이라 다음 페이지를 이을 기준으로 쓴다 */
    private static String encodeCursor(LocalDate periodEnd) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(periodEnd.toString().getBytes(StandardCharsets.UTF_8));
    }

    /** 깨졌거나 날짜가 아니거나 DB 가 받는 연도(1~9999)를 벗어난 커서는 400 (벗어난 값을 그대로 쿼리에 넣으면 DB 오류로 500 이 된다) */
    private static LocalDate decodeCursor(String cursor) {
        try {
            LocalDate periodEnd = LocalDate.parse(new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8));
            if (periodEnd.getYear() < 1 || periodEnd.getYear() > 9999) throw new GeneralException(ErrorStatus.INVALID_INPUT);
            return periodEnd;
        } catch (IllegalArgumentException | DateTimeParseException e) {
            throw new GeneralException(ErrorStatus.INVALID_INPUT);
        }
    }
}
