package io.pinkspider.global.event;

import io.pinkspider.global.enums.TitleRarity;
import java.time.LocalDateTime;

/**
 * 칭호 장착/해제 이벤트
 * - 피드의 칭호 정보 업데이트에 사용
 */
public record TitleEquippedEvent(
    String userId,
    String titleName,
    TitleRarity titleRarity,
    String titleColorCode,
    LocalDateTime occurredAt
) implements DomainEvent {

    public TitleEquippedEvent(String userId, String titleName, TitleRarity titleRarity, String titleColorCode) {
        this(userId, titleName, titleRarity, titleColorCode, LocalDateTime.now());
    }
}
