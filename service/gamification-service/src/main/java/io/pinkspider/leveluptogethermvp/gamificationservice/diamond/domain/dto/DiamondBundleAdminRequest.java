package io.pinkspider.leveluptogethermvp.gamificationservice.diamond.domain.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** LUT-356: 핑크다이아 묶음상품 등록/수정 요청 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonNaming(SnakeCaseStrategy.class)
public class DiamondBundleAdminRequest {

    @NotBlank(message = "상품명은 필수입니다.")
    @Size(max = 50, message = "상품명은 50자 이하이어야 합니다.")
    private String name;

    @Size(max = 50, message = "상품명(영어)은 50자 이하이어야 합니다.")
    private String nameEn;

    @Size(max = 50, message = "상품명(아랍어)은 50자 이하이어야 합니다.")
    private String nameAr;

    @Size(max = 50, message = "상품명(일본어)은 50자 이하이어야 합니다.")
    private String nameJa;

    @Size(max = 2000, message = "상품 설명은 2000자 이하이어야 합니다.")
    private String description;

    @Size(max = 2000, message = "상품 설명(영어)은 2000자 이하이어야 합니다.")
    private String descriptionEn;

    @Size(max = 2000, message = "상품 설명(아랍어)은 2000자 이하이어야 합니다.")
    private String descriptionAr;

    @Size(max = 2000, message = "상품 설명(일본어)은 2000자 이하이어야 합니다.")
    private String descriptionJa;

    @NotNull(message = "다이아 개수는 필수입니다.")
    @Positive(message = "다이아 개수는 1 이상이어야 합니다.")
    private Integer diamondCount;

    @Size(max = 500, message = "이미지 URL은 500자 이하이어야 합니다.")
    private String imageUrl;

    private Boolean isActive;
}
