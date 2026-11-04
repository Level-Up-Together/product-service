package io.pinkspider.leveluptogethermvp.towerservice.domain.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.pinkspider.leveluptogethermvp.towerservice.domain.entity.TowerNpc;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * LUT-693: 어드민 NPC 등록·수정 요청 (admin-service 패스스루 LUT-694 → 내부 API). 자리는 배경 원본 픽셀, 마스크 안이어야 한다. 대사는
 * 1개 이상, 순서대로 돌아간다. 상태 문구는 선택(비우면 표시 없음).
 */
@JsonNaming(SnakeCaseStrategy.class)
public record TowerNpcAdminRequest(
        @NotBlank @Size(max = 30) String name,
        @NotBlank @Size(max = 30) String nameEn,
        @NotBlank @Size(max = 30) String nameJa,
        @NotBlank @Size(max = 30) String nameAr,
        @NotBlank
                @Size(max = 500)
                @Pattern(regexp = TowerFloorAdminRequest.HTTPS_URL, message = "https URL 만 허용합니다")
                String profileImageUrl,
        @Size(max = 30) String statusText,
        @Size(max = 30) String statusTextEn,
        @Size(max = 30) String statusTextJa,
        @Size(max = 30) String statusTextAr,
        @NotNull @Min(0) Integer seatX,
        @NotNull @Min(0) Integer seatY,
        @NotEmpty @Size(max = 50) List<@Valid @NotNull TowerNpcLineDto> lines,
        @NotNull Boolean isPublic,
        /** 비우면 0 — 같은 층 안에서 작은 값이 먼저 */
        @Min(0) Integer sortOrder) {

    public TowerNpc toEntity(Long floorId) {
        return TowerNpc.builder()
                .floorId(floorId)
                .name(name.trim())
                .nameEn(nameEn.trim())
                .nameJa(nameJa.trim())
                .nameAr(nameAr.trim())
                .profileImageUrl(profileImageUrl)
                .statusText(blankToNull(statusText))
                .statusTextEn(blankToNull(statusTextEn))
                .statusTextJa(blankToNull(statusTextJa))
                .statusTextAr(blankToNull(statusTextAr))
                .seatX(seatX)
                .seatY(seatY)
                .isPublic(isPublic)
                .sortOrder(sortOrder != null ? sortOrder : 0)
                .lines(
                        new java.util.ArrayList<>(
                                lines.stream().map(TowerNpcLineDto::toEntity).toList()))
                .build();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
