package com.cheotdanchu.dto;

import com.cheotdanchu.entity.Mission;

public record MissionSubmitResponse(Long submissionId, Mission.Status missionStatus) {}
