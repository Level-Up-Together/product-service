package io.pinkspider.leveluptogethermvp.towerservice.domain.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.pinkspider.leveluptogethermvp.towerservice.domain.entity.TowerFloor;
import io.pinkspider.leveluptogethermvp.towerservice.domain.entity.TowerFloorPixelArt;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/**
 * LUT-629: 유저용 층 응답. 이름 4개 locale 을 전부 내려 클라이언트가 현재 locale 로 고른다(상점 아이템과 같은 방식). {@code locked} 는
 * 서버 판정 — 로그인 유저는 레벨 부족, 방문객은 비로그인 비허용 층. {@code population} 은 층 전체 인원(LUT-630, 30초 메모, NPC 제외).
 * LUT-725: 앞 가림 마스크(없으면 null)·불빛 점·오늘(KST) 켜진 효과 레이어(추가 필드). LUT-727: {@code background_image_url}
 * 이 원본, {@code pixel_dense}(높이 576, 입장 기본)·{@code pixel_standard}(높이 384) 가 픽셀 배경(추가 필드).
 */
@JsonNaming(SnakeCaseStrategy.class)
public record TowerFloorResponse(
        Long id,
        Integer floorNumber,
        String name,
        String nameEn,
        String nameJa,
        String nameAr,
        Integer entryLevel,
        Boolean locked,
        Boolean guestAllowed,
        String backgroundImageUrl,
        String floorMaskUrl,
        Integer entryPointX,
        Integer entryPointY,
        String bgmUrl,
        Integer instanceCapacity,
        Long population,
        String occlusionMaskUrl,
        List<TowerFloorLightDto> lights,
        List<TowerFloorEffectLayerDto> effectLayers,
        TowerPixelBackgroundDto pixelDense,
        TowerPixelBackgroundDto pixelStandard,
        TowerFloorGuideView guide) {

    public static TowerFloorResponse from(TowerFloor f, boolean locked, long population) {
        return from(f, locked, population, null, LocalDate.now(KST));
    }

    public static TowerFloorResponse from(
            TowerFloor f, boolean locked, long population, TowerFloorPixelArt pixelArt) {
        return from(f, locked, population, pixelArt, LocalDate.now(KST));
    }

    /** LUT-734: 첫 방문 안내(살아 있는 안내만, 아니면 null)를 함께 */
    public static TowerFloorResponse from(
            TowerFloor f,
            boolean locked,
            long population,
            TowerFloorPixelArt pixelArt,
            TowerFloorGuideView guide) {
        return from(f, locked, population, pixelArt, LocalDate.now(KST), guide);
    }

    public static TowerFloorResponse from(
            TowerFloor f,
            boolean locked,
            long population,
            TowerFloorPixelArt pixelArt,
            LocalDate today) {
        return from(f, locked, population, pixelArt, today, null);
    }

    /**
     * LUT-725: 효과 레이어는 {@code today}(KST)에 켜진 것만 내린다. LUT-727: 픽셀 배경은 지금 배경으로 만든 완료 결과만 — 변환 전·실패·옛
     * 배경 결과면 null(프론트는 원본으로 보여 준다)
     */
    public static TowerFloorResponse from(
            TowerFloor f,
            boolean locked,
            long population,
            TowerFloorPixelArt pixelArt,
            LocalDate today,
            TowerFloorGuideView guide) {
        boolean ready = pixelArt != null && pixelArt.isReadyFor(f.getBackgroundImageUrl());
        return new TowerFloorResponse(
                f.getId(),
                f.getFloorNumber(),
                f.getName(),
                f.getNameEn(),
                f.getNameJa(),
                f.getNameAr(),
                f.getEntryLevel(),
                locked,
                f.isGuestAllowed(),
                f.getBackgroundImageUrl(),
                f.getFloorMaskUrl(),
                f.getEntryPointX(),
                f.getEntryPointY(),
                f.getBgmUrl(),
                f.getInstanceCapacity(),
                population,
                f.getOcclusionMaskUrl(),
                f.getLights().stream().map(TowerFloorLightDto::from).toList(),
                f.activeEffectLayers(today).stream().map(TowerFloorEffectLayerDto::from).toList(),
                ready ? TowerPixelBackgroundDto.from(pixelArt.getDense()) : null,
                ready ? TowerPixelBackgroundDto.from(pixelArt.getStandard()) : null,
                guide);
    }

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
}
