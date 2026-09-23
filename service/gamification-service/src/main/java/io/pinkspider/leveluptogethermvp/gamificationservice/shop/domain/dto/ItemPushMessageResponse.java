package io.pinkspider.leveluptogethermvp.gamificationservice.shop.domain.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.domain.entity.ItemPushMessage;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** LUT-516: 장착 아이템 개별 푸시 메시지 응답 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonNaming(SnakeCaseStrategy.class)
public class ItemPushMessageResponse {

    private Long id;
    private Long shopItemId;
    private String message;
    private String messageEn;
    private String messageAr;
    private String messageJa;
    private String sendTime;
    private Boolean enabled;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime modifiedAt;

    public static ItemPushMessageResponse from(ItemPushMessage entity) {
        return ItemPushMessageResponse.builder()
                .id(entity.getId())
                .shopItemId(entity.getShopItem().getId())
                .message(entity.getMessage())
                .messageEn(entity.getMessageEn())
                .messageAr(entity.getMessageAr())
                .messageJa(entity.getMessageJa())
                .sendTime(entity.getSendTime())
                .enabled(entity.getEnabled())
                .createdBy(entity.getCreatedBy())
                .createdAt(entity.getCreatedAt())
                .modifiedAt(entity.getModifiedAt())
                .build();
    }
}
