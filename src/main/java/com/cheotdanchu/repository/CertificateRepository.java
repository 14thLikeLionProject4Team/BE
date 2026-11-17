package com.cheotdanchu.repository;

import com.cheotdanchu.entity.Certificate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CertificateRepository extends JpaRepository<Certificate, Long> {

    /** 가장 최근에 발급한 확인서 (발급 시각이 같으면 나중에 만든 것) */
    Optional<Certificate> findFirstByMemberIdOrderByIssuedAtDescCertificateIdDesc(Long memberId);

    /** 본인 확인서만 찾는다 (남의 확인서는 없는 것과 같게 보이도록 memberId 까지 함께 조회) */
    Optional<Certificate> findByCertificateIdAndMemberId(Long certificateId, Long memberId);
}
