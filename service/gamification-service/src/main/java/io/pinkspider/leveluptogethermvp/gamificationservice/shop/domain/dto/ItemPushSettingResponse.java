package io.pinkspider.leveluptogethermvp.gamificationservice.shop.domain.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.domain.entity.ItemPushSetting;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** LUT-528/573: 장착 아이템 푸시 발송 설정 응답 — 아침/오후 슬롯 시각 (null = 그 슬롯 미발송) */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonNaming(SnakeCaseStrategy.class)
public class ItemPushSettingResponse {

    private Long shopItemId;
    private String beforeActivityTime;
    private String afterCompleteTime;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime modifiedAt;

    public static ItemPushSettingResponse from(ItemPushSetting entity) {
        return ItemPushSettingResponse.builder()
                .shopItemId(entity.getShopItemId())
                .beforeActivityTime(entity.getBeforeActivityTime())
                .afterCompleteTime(entity.getAfterCompleteTime())
                .createdBy(entity.getCreatedBy())
                .createdAt(entity.getCreatedAt())
                .modifiedAt(entity.getModifiedAt())
                .build();
    }
}
