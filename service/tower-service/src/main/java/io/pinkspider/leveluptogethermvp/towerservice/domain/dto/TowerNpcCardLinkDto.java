package io.pinkspider.leveluptogethermvp.towerservice.domain.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.pinkspider.leveluptogethermvp.towerservice.domain.enums.TowerNpcCardLinkType;
import jakarta.validation.constraints.Size;

/**
 * LUT-733: NPC 창 이동 링크 — {@code type} SHOP_ITEM·EVENT 는 {@code target_id}(아이템·이벤트 ID), PATH 는
 * {@code path}(앱 경로, {@code /} 로 시작), SUBSCRIPTION 은 대상 없음. 종류가 비면 링크 없음.
 */
@JsonNaming(SnakeCaseStrategy.class)
public record TowerNpcCardLinkDto(
        TowerNpcCardLinkType type, Long targetId, @Size(max = 300) String path) {}
