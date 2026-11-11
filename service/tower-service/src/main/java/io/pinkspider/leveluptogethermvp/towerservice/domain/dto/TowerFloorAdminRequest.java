package io.pinkspider.leveluptogethermvp.towerservice.domain.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.pinkspider.leveluptogethermvp.towerservice.domain.entity.TowerFloor;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.ArrayList;
import java.util.List;

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
        @NotNull Boolean isPublic,
        /** LUT-725: 앞 가림 마스크 — null 이면 그대로(구 어드민), 빈 문자열이면 지운다 */
        @Size(max = 500) @Pattern(regexp = "^$|" + HTTPS_URL, message = "https URL 만 허용합니다")
                String occlusionMaskUrl,
        /** LUT-725: 불빛 점(최대 50개) — null 이면 그대로, [] 이면 모두 지운다 */
        @Size(max = 50) List<@Valid @NotNull TowerFloorLightDto> lights,
        /**
         * LUT-725: 효과 레이어(종류당 1개 — SNOW·STARS·CLOUD·RAIN) — null 이면 그대로, [] 이면 모두 지운다. LUT-785: 상한을
         * 종류 수(4)로 (예전 2 는 CLOUD 가 생긴 뒤 세 종류를 한 층에 다 켤 수 없었다)
         */
        @Size(max = 4) List<@Valid @NotNull TowerFloorEffectLayerDto> effectLayers) {

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
                .occlusionMaskUrl(
                        occlusionMaskUrl == null || occlusionMaskUrl.isBlank()
                                ? null
                                : occlusionMaskUrl)
                .lights(
                        lights == null
                                ? new ArrayList<>()
                                : new ArrayList<>(
                                        lights.stream()
                                                .map(TowerFloorLightDto::toEmbeddable)
                                                .toList()))
                .effectLayers(
                        effectLayers == null
                                ? new ArrayList<>()
                                : new ArrayList<>(
                                        effectLayers.stream()
                                                .map(TowerFloorEffectLayerDto::toEmbeddable)
                                                .toList()))
                .build();
    }
}
