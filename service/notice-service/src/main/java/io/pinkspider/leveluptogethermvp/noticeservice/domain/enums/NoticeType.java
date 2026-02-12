package io.pinkspider.leveluptogethermvp.noticeservice.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum NoticeType {
    GENERAL("일반"),
    EVENT("이벤트"),
    MAINTENANCE("점검"),
    UPDATE("업데이트");

    private final String description;
}
