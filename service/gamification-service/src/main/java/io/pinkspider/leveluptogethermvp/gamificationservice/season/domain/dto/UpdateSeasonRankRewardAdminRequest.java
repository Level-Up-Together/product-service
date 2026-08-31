package io.pinkspider.leveluptogethermvp.gamificationservice.season.domain.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.pinkspider.global.enums.TitlePosition;
import io.pinkspider.global.enums.TitleRarity;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@JsonNaming(SnakeCaseStrategy.class)
public record UpdateSeasonRankRewardAdminRequest(
    @NotNull @Min(1) Integer rankStart,
    @NotNull @Min(1) Integer rankEnd,
    Long categoryId,
    String categoryName,
    /* LUT-420: 지정 시 기존 칭호 참조(메타 수정 없음), 미지정 시 titleName 등으로 새 칭호 생성 후 교체 */
    Long titleId,
    /* LUT-420: 신규 생성 모드에서만 필수 — 참조 모드는 무시 (서비스에서 검증) */
    String titleName,
    /* LUT-420: 신규 생성용 칭호명 로케일 변형 (선택, 미입력 시 ko 폴백) */
    String titleNameEn,
    String titleNameAr,
    String titleNameJa,
    TitleRarity titleRarity,
    TitlePosition titlePositionType,
    /* LUT-339: 보상 아이템 (shop_item ID, 선택) — 칭호와 동시 지급 */
    Long itemId,
    Integer sortOrder
) {}
