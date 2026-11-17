package com.cheotdanchu.service;

import com.cheotdanchu.ai.SeedRepository;
import com.cheotdanchu.common.exception.GeneralException;
import com.cheotdanchu.common.response.ErrorStatus;
import com.cheotdanchu.dto.CertificateDtos;
import com.cheotdanchu.dto.RecordsSummaryResponse;
import com.cheotdanchu.entity.Certificate;
import com.cheotdanchu.repository.CertificateRepository;
import com.cheotdanchu.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 발급한 확인서(자녀 전용) */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CertificateService {

    private final MemberRepository memberRepository;
    private final CertificateRepository certificateRepository;
    private final SeedRepository seedRepository;
    private final RecordsService recordsService;

    /** 발급하기 전에 확인서에 들어갈 지표. 기록 지표 조회와 같은 계산이고 발급 요건을 채우지 않아도 볼 수 있다 */
    public RecordsSummaryResponse getPreview(Long personaId) {
        return recordsService.getSummary(personaId);
    }

    /** 가장 최근에 발급한 확인서. 만료됐어도 내려주고 expired 로 알린다. 발급한 적이 없으면 404 */
    public CertificateDtos.LatestResponse getLatest(Long personaId) {
        Long memberId = memberRepository.getChild(personaId).getMemberId();
        Certificate c = certificateRepository.findFirstByMemberIdOrderByIssuedAtDescCertificateIdDesc(memberId)
                .orElseThrow(() -> new GeneralException(ErrorStatus.CERTIFICATE_NOT_FOUND));

        return new CertificateDtos.LatestResponse(
                c.getCertificateId(), c.getFullPaidMonths(), c.getTotalMonths(), c.getMaxStreak(),
                c.getAvgMarginRate().doubleValue(), c.getSelfResolvedCount(), c.getShortfallCount(),
                c.getIssuedAt(), c.getExpiresAt(), c.isExpiredOn(seedRepository.today()));
    }
}
