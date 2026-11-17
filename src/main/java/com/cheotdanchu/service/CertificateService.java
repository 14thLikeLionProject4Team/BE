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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.security.SecureRandom;
import java.util.HexFormat;

/** 발급한 확인서(자녀 전용) */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CertificateService {

    /** 발급한 뒤 유효한 기간(일) */
    private static final int VALID_DAYS = 90;

    private static final SecureRandom RANDOM = new SecureRandom();

    private final MemberRepository memberRepository;
    private final CertificateRepository certificateRepository;
    private final SeedRepository seedRepository;
    private final RecordsService recordsService;
    private final CertificateEligibilityService eligibilityService;
    private final CertificatePdfWriter pdfWriter;
    private final CertificateStorage storage;

    /** 공유 링크 주소의 앞부분. 링크를 받은 사람이 여는 화면은 이번 범위가 아니라 시연용 주소를 기본값으로 둔다 */
    @Value("${app.share-base-url:https://example.com/certificates/shared/}")
    private String shareBaseUrl;

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
        LocalDateTime issuedAt = now();
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

    /**
     * 발급한 확인서를 PDF 로 내려준다. 본인 확인서만(남의 것·없는 것은 404), 만료됐으면 410.
     * 만료 판단은 최신 조회와 같은 Certificate.isExpiredOn 을 쓴다.
     */
    public CertificateDtos.DownloadFile download(Long personaId, Long certificateId) {
        Certificate c = validOwnCertificate(personaId, certificateId);

        byte[] pdf = storage.read(c.getFilePath()).orElseThrow(() -> new GeneralException(ErrorStatus.CERTIFICATE_FILE_MISSING));
        return new CertificateDtos.DownloadFile("certificate_" + c.getCertificateId() + ".pdf", pdf);
    }

    /**
     * 공유 링크를 만든다. 본인의 유효한 확인서만(남의 것·없는 것은 404, 만료됐으면 410).
     * 링크는 지금부터 expiresInHours 시간 동안 유효하고, 확인서 유효기한(날짜 단위)을 넘길 수 없다(400).
     * 확인서마다 링크 하나라서 다시 만들면 이전 링크는 못 쓰게 된다.
     */
    @Transactional
    public CertificateDtos.ShareResponse share(Long personaId, Long certificateId, int expiresInHours) {
        Certificate c = validOwnCertificate(personaId, certificateId);

        LocalDateTime shareExpiresAt = now().plusHours(expiresInHours);
        // 확인서 유효 판단(isExpiredOn)과 같은 날짜 단위로 비교한다: 만료일 당일까지는 공유 링크도 만들 수 있다
        if (shareExpiresAt.toLocalDate().isAfter(c.getExpiresAt().toLocalDate())) {
            throw new GeneralException(ErrorStatus.INVALID_INPUT, "확인서 유효기한(" + c.getExpiresAt().toLocalDate() + ")을 넘겨서 공유할 수 없어요");
        }
        String token = newShareToken();
        c.share(token, shareExpiresAt);
        String base = shareBaseUrl.endsWith("/") ? shareBaseUrl : shareBaseUrl + "/"; // 설정에 마지막 / 가 없어도 주소가 깨지지 않게
        return new CertificateDtos.ShareResponse(base + token, shareExpiresAt);
    }

    /** 본인 확인서를 찾고 아직 유효한지 확인한다 (남의 것·없는 것은 404, 만료됐으면 410) */
    private Certificate validOwnCertificate(Long personaId, Long certificateId) {
        Long memberId = memberRepository.getChild(personaId).getMemberId();
        Certificate c = certificateRepository.findByCertificateIdAndMemberId(certificateId, memberId)
                .orElseThrow(() -> new GeneralException(ErrorStatus.CERTIFICATE_NOT_FOUND));
        if (c.isExpiredOn(seedRepository.today())) throw new GeneralException(ErrorStatus.CERTIFICATE_EXPIRED);
        return c;
    }

    /** 시연 기준일의 날짜에 지금 시각을 붙인 현재 시각 (발급·공유 만료·만료 판단이 같은 시계를 쓴다) */
    private LocalDateTime now() {
        return LocalDateTime.of(seedRepository.today(), LocalTime.now().truncatedTo(ChronoUnit.SECONDS));
    }

    /** 추측할 수 없는 토큰: 무작위 16바이트를 16진수 32자로 */
    private static String newShareToken() {
        byte[] bytes = new byte[16];
        RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
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
