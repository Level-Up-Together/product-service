package io.pinkspider.leveluptogethermvp.supportservice.report.api.dto;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ReportTargetType {
    USER_PROFILE("사용자 프로필"),
    FEED("피드"),
    FEED_COMMENT("피드 댓글"),
    GUILD("길드");

    private final String description;
}
