package io.pinkspider.leveluptogethermvp.gamificationservice.season.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum SeasonRewardStatus {
    PENDING("대기"),
    SUCCESS("성공"),
    FAILED("실패"),
    SKIPPED("건너뜀");

    private final String description;
}
