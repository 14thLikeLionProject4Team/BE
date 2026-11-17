package com.cheotdanchu.service;

import com.cheotdanchu.common.exception.GeneralException;
import com.cheotdanchu.common.response.ErrorStatus;
import com.cheotdanchu.dto.AllowanceChangeDtos;
import com.cheotdanchu.entity.AllowanceChange;
import com.cheotdanchu.entity.Member;
import com.cheotdanchu.repository.AllowanceChangeRepository;
import com.cheotdanchu.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.List;

/** 용돈 변경 이력(마이 부모) */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AllowanceHistoryService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;
    /** 첫 페이지는 커서를 이 먼 미래 시각으로 둬서 처음부터 읽는다 */
    private static final LocalDateTime FAR_FUTURE = LocalDateTime.of(9999, 12, 31, 0, 0);

    private final MemberRepository memberRepository;
    private final AllowanceChangeRepository allowanceChangeRepository;

    /** 용돈이 바뀐 이력을 최신(만든 시각이 늦은 것)부터. size 는 기본 20(최대 100), 다음 페이지는 응답의 nextCursor 를 그대로 보낸다 */
    public AllowanceChangeDtos.HistoryResponse getHistory(Long personaId, Integer size, String cursor) {
        Member parent = memberRepository.getParent(personaId);
        int pageSize = size == null ? DEFAULT_PAGE_SIZE : size;
        if (pageSize < 1 || pageSize > MAX_PAGE_SIZE) throw new GeneralException(ErrorStatus.INVALID_INPUT);

        Long familyId = parent.getFamily().getFamilyId();
        Cursor from = cursor == null ? new Cursor(FAR_FUTURE, Long.MAX_VALUE) : decodeCursor(cursor);
        // 다음 페이지가 있는지 알려고 한 건을 더 읽는다
        List<AllowanceChange> page = allowanceChangeRepository.page(familyId, from.createdAt(), from.id(), PageRequest.of(0, pageSize + 1));

        boolean hasNext = page.size() > pageSize;
        List<AllowanceChange> shown = hasNext ? page.subList(0, pageSize) : page;
        return new AllowanceChangeDtos.HistoryResponse(
                (int) allowanceChangeRepository.countByFamilyIdAndReason(familyId, AllowanceChange.Reason.RENEGOTIATION),
                shown.stream().map(c -> new AllowanceChangeDtos.HistoryItem(c.getChangeId(), c.getPrevAmount(), c.getAmount(),
                        c.getReason(), c.getSource(), c.getEffectiveFrom(), c.getCreatedAt())).toList(),
                hasNext ? encodeCursor(shown.get(shown.size() - 1)) : null);
    }

    private record Cursor(LocalDateTime createdAt, long id) {}

    private static String encodeCursor(AllowanceChange last) {
        String raw = last.getCreatedAt() + "|" + last.getChangeId();
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    /** 깨졌거나 시각·숫자가 아니거나 DB 가 받는 연도(1~9999)를 벗어난 커서는 400 (벗어난 값을 그대로 쿼리에 넣으면 DB 오류로 500 이 된다) */
    private static Cursor decodeCursor(String cursor) {
        try {
            String[] parts = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8).split("\\|", 2);
            LocalDateTime createdAt = LocalDateTime.parse(parts[0]);
            if (parts.length != 2 || createdAt.getYear() < 1 || createdAt.getYear() > 9999) throw new GeneralException(ErrorStatus.INVALID_INPUT);
            return new Cursor(createdAt, Long.parseLong(parts[1]));
        } catch (IllegalArgumentException | DateTimeParseException e) {
            throw new GeneralException(ErrorStatus.INVALID_INPUT);
        }
    }
}
