package com.cheotdanchu.service;

import com.cheotdanchu.ai.SeedRepository;
import com.cheotdanchu.common.exception.GeneralException;
import com.cheotdanchu.common.response.ErrorStatus;
import com.cheotdanchu.dto.MissionListResponse;
import com.cheotdanchu.entity.Member;
import com.cheotdanchu.entity.Mission;
import com.cheotdanchu.entity.MissionSubmission;
import com.cheotdanchu.repository.MemberRepository;
import com.cheotdanchu.repository.MissionRepository;
import com.cheotdanchu.repository.MissionSubmissionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MissionService {

    private static final EnumSet<Mission.Status> ACTIVE =
            EnumSet.of(Mission.Status.NEW, Mission.Status.IN_PROGRESS, Mission.Status.SUBMITTED);

    private final MemberRepository memberRepository;
    private final MissionRepository missionRepository;
    private final MissionSubmissionRepository submissionRepository;
    private final SeedRepository seedRepository;

    public MissionListResponse getMissions(Long personaId, String status) {
        if (!"active".equals(status)) throw new GeneralException(ErrorStatus.INVALID_MISSION_STATUS);

        Member member = memberRepository.findById(personaId)
                .orElseThrow(() -> new GeneralException(ErrorStatus.PERSONA_NOT_FOUND));

        LocalDate today = seedRepository.today();
        List<MissionListResponse.MissionDto> missions = missionRepository
                .findByFamily_FamilyIdAndStatusInOrderByMissionId(member.getFamily().getFamilyId(), ACTIVE)
                .stream()
                .map(m -> toDto(m, today))
                .toList();

        int totalReward = missions.stream()
                .map(MissionListResponse.MissionDto::rewardAmount)
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .sum();

        return new MissionListResponse(missions.size(), totalReward, missions);
    }

    private MissionListResponse.MissionDto toDto(Mission m, LocalDate today) {
        int done = (int) submissionRepository.countByMission_MissionIdAndReviewStatus(
                m.getMissionId(), MissionSubmission.ReviewStatus.APPROVED);
        int target = m.targetCount();
        int rate = Math.min(100, done * 100 / target);

        return new MissionListResponse.MissionDto(
                m.getMissionId(), m.getTitle(), m.getMissionType(), m.getStatus(), m.getRewardAmount(),
                new MissionListResponse.Progress(done, target, rate),
                description(m, today),
                m.getStartDate(), m.getEndDate());
    }

    private String description(Mission m, LocalDate today) {
        String until = m.getEndDate() == null ? "" : m.getEndDate().getMonthValue() + "월 " + m.getEndDate().getDayOfMonth() + "일까지";
        return switch (m.getStatus()) {
            case NEW -> today.equals(m.getStartDate()) ? "오늘 열었어요 · " + until : until;
            case SUBMITTED -> "부모님 확인 기다리는 중";
            default -> until;
        };
    }
}
