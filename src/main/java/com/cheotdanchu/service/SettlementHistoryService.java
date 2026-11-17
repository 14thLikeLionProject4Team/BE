package com.cheotdanchu.service;

import com.cheotdanchu.common.exception.GeneralException;
import com.cheotdanchu.common.response.ErrorStatus;
import com.cheotdanchu.dto.SettlementHistoryDtos;
import com.cheotdanchu.entity.Member;
import com.cheotdanchu.entity.Settlement;
import com.cheotdanchu.repository.MemberRepository;
import com.cheotdanchu.repository.SettlementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.List;

/** 마이(자녀)의 결제일 기록 (자녀 전용) */
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

    private final MemberRepository memberRepository;
    private final SettlementRepository settlementRepository;

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
