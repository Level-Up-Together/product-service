package io.pinkspider.leveluptogethermvp.gamificationservice.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum TitleAcquisitionType {
    LEVEL("레벨 달성"),
    ACHIEVEMENT("업적 달성"),
    MISSION("미션 완료"),
    GUILD("길드 활동"),
    EVENT("이벤트"),
    SPECIAL("특별");

    private final String description;
}
