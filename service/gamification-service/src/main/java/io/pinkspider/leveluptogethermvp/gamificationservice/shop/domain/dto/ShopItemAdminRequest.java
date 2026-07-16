package io.pinkspider.leveluptogethermvp.gamificationservice.shop.domain.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.pinkspider.global.enums.TitleRarity;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.domain.enums.ShopItemImagePosition;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.domain.enums.ShopItemType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonNaming(SnakeCaseStrategy.class)
public class ShopItemAdminRequest {

    @NotBlank(message = "아이템명은 필수입니다.")
    @Size(max = 50, message = "아이템명은 50자 이하이어야 합니다.")
    private String name;

    @Size(max = 50, message = "아이템명(영어)은 50자 이하이어야 합니다.")
    private String nameEn;

    @Size(max = 50, message = "아이템명(아랍어)은 50자 이하이어야 합니다.")
    private String nameAr;

    @Size(max = 50, message = "아이템명(일본어)은 50자 이하이어야 합니다.")
    private String nameJa;

    @NotNull(message = "아이템 타입은 필수입니다.")
    private ShopItemType itemType;

    @NotNull(message = "희귀도는 필수입니다.")
    private TitleRarity rarity;

    @Size(max = 500, message = "이미지 URL은 500자 이하이어야 합니다.")
    private String imageUrl;

    /** 이미지 포지션 — 미지정 시 BACK (LUT-225) */
    private ShopItemImagePosition imagePosition;

    @NotNull(message = "가격은 필수입니다.")
    @PositiveOrZero(message = "가격은 0 이상이어야 합니다.")
    private Integer price;

    private Boolean isActive;
}
