package com.cheotdanchu.service;

import com.cheotdanchu.ai.SeedRepository;
import com.cheotdanchu.common.config.CertificateStorage;
import com.cheotdanchu.common.exception.GeneralException;
import com.cheotdanchu.common.response.ErrorStatus;
import com.cheotdanchu.dto.CertificateDtos;
import com.cheotdanchu.dto.RecordsSummaryResponse;
import com.cheotdanchu.entity.Certificate;
import com.cheotdanchu.entity.Member;
import com.cheotdanchu.repository.CertificateRepository;
import com.cheotdanchu.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;

/** 발급한 확인서(자녀 전용) */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CertificateService {

    /** 발급한 뒤 유효한 기간(일) */
    private static final int VALID_DAYS = 90;

    private final MemberRepository memberRepository;
    private final CertificateRepository certificateRepository;
    private final SeedRepository seedRepository;
    private final RecordsService recordsService;
    private final CertificateEligibilityService eligibilityService;
    private final CertificatePdfWriter pdfWriter;
    private final CertificateStorage storage;

    /** 발급하기 전에 확인서에 들어갈 지표. 기록 지표 조회와 같은 계산이고 발급 요건을 채우지 않아도 볼 수 있다 */
    public RecordsSummaryResponse getPreview(Long personaId) {
        return recordsService.getSummary(personaId);
    }

    /**
     * 확인서를 발급한다: 발급 요건(만 19세, 검증된 정산 12회)을 채웠을 때만, 그때의 지표로 PDF 를 만들어 저장한다.
     * 이미 유효한 확인서가 있으면 새로 만들지 않는다(발급 버튼 연타로 여러 장이 생기지 않게 자녀 행에 잠금을 걸고 확인한다).
     * 발급 시각은 시연 기준일(SeedRepository.today)의 날짜에 지금 시각을 붙여서, 만료 판단과 같은 시계를 쓴다. 유효기간은 90일이다.
     * 잠금을 기다리다 풀린 뒤에는 앞 요청이 저장한 확인서가 보여야 해서 READ_COMMITTED 로 읽는다.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public CertificateDtos.IssueResponse issue(Long personaId, boolean identityVerified) {
        Member child = memberRepository.getChild(personaId);
        if (!identityVerified) throw new GeneralException(ErrorStatus.IDENTITY_NOT_VERIFIED);
        memberRepository.findByIdForUpdate(child.getMemberId());

        LocalDate today = seedRepository.today();
        CertificateDtos.EligibilityResponse eligibility = eligibilityService.getEligibility(child);
        if (!eligibility.canIssue()) {
            throw new GeneralException(ErrorStatus.CERTIFICATE_NOT_AVAILABLE, notAvailableMessage(eligibility.availableFrom(), today));
        }
        if (certificateRepository.findFirstByMemberIdOrderByIssuedAtDescCertificateIdDesc(child.getMemberId())
                .filter(c -> !c.isExpiredOn(today)).isPresent()) {
            throw new GeneralException(ErrorStatus.CERTIFICATE_ALREADY_ISSUED);
        }

        RecordsSummaryResponse records = recordsService.getSummary(personaId);
        LocalDateTime issuedAt = LocalDateTime.of(today, LocalTime.now().truncatedTo(ChronoUnit.SECONDS));
        LocalDateTime expiresAt = issuedAt.plusDays(VALID_DAYS);
        String fileName = storage.save(pdfWriter.write(child.getName(), records, issuedAt, expiresAt));
        deleteFileIfRolledBack(fileName);

        Certificate saved = certificateRepository.save(Certificate.issue(child.getMemberId(), records, fileName, issuedAt, expiresAt));
        return new CertificateDtos.IssueResponse(saved.getCertificateId(), saved.getIssuedAt(), saved.getExpiresAt());
    }

    /** 가능일이 아직 오지 않았으면 그 날짜를, 가능일이 오늘이거나 지났는데도 못 채웠으면(정산 지연 등) 날짜 없이 알린다 */
    private static String notAvailableMessage(LocalDate availableFrom, LocalDate today) {
        if (!availableFrom.isAfter(today)) return "아직 발급 요건을 채우지 못했어요";
        return String.format("%d월 %d일부터 발급할 수 있어요", availableFrom.getMonthValue(), availableFrom.getDayOfMonth());
    }

    /** DB 저장이 롤백되면 방금 저장한 PDF 도 지운다 (가리키는 행 없는 파일이 남지 않게) */
    private void deleteFileIfRolledBack(String fileName) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) storage.delete(fileName);
            }
        });
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
