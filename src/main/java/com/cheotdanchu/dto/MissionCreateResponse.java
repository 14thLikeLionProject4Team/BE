package com.cheotdanchu.dto;

import com.cheotdanchu.entity.Mission;

public record MissionCreateResponse(Long missionId, Mission.Status status) {}
