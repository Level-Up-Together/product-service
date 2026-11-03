package io.pinkspider.leveluptogethermvp.towerservice.domain.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.pinkspider.leveluptogethermvp.towerservice.domain.entity.TowerFloor;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * LUT-629: 어드민 층 등록·수정 요청 (admin-service 패스스루 → 내부 API). 좌표는 배경 이미지 원본 픽셀 기준. 이미지·BGM URL 은 https
 * 만.
 */
@JsonNaming(SnakeCaseStrategy.class)
public record TowerFloorAdminRequest(
        @NotNull @Min(1) Integer floorNumber,
        @NotBlank @Size(max = 100) String name,
        @NotBlank @Size(max = 100) String nameEn,
        @NotBlank @Size(max = 100) String nameJa,
        @NotBlank @Size(max = 100) String nameAr,
        /** 비우면 제한 없음 */
        @Min(1) Integer entryLevel,
        @NotNull Boolean guestAllowed,
        @NotBlank @Size(max = 500) @Pattern(regexp = HTTPS_URL, message = "https URL 만 허용합니다")
                String backgroundImageUrl,
        @NotBlank @Size(max = 500) @Pattern(regexp = HTTPS_URL, message = "https URL 만 허용합니다")
                String floorMaskUrl,
        @NotNull @Min(0) Integer entryPointX,
        @NotNull @Min(0) Integer entryPointY,
        @Size(max = 500) @Pattern(regexp = HTTPS_URL, message = "https URL 만 허용합니다") String bgmUrl,
        /** 비우면 30 */
        @Min(1) Integer instanceCapacity,
        @NotNull Boolean isPublic) {

    /** 서버가 이 URL 을 읽으므로(마스크 검사) https 만 받는다 — 호스트 허용 목록은 ImageFloorMaskChecker */
    public static final String HTTPS_URL = "^https://.+";

    public TowerFloor toEntity() {
        return TowerFloor.builder()
                .floorNumber(floorNumber)
                .name(name)
                .nameEn(nameEn)
                .nameJa(nameJa)
                .nameAr(nameAr)
                .entryLevel(entryLevel)
                .guestAllowed(guestAllowed)
                .backgroundImageUrl(backgroundImageUrl)
                .floorMaskUrl(floorMaskUrl)
                .entryPointX(entryPointX)
                .entryPointY(entryPointY)
                .bgmUrl(bgmUrl)
                .instanceCapacity(
                        instanceCapacity != null
                                ? instanceCapacity
                                : TowerFloor.DEFAULT_INSTANCE_CAPACITY)
                .isPublic(isPublic)
                .build();
    }
}
