package io.pinkspider.leveluptogethermvp.towerservice.domain.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.pinkspider.leveluptogethermvp.towerservice.domain.entity.TowerFloorGuide;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.ArrayList;
import java.util.List;

/**
 * LUT-734: 첫 방문 안내 저장(층당 1행, 없으면 만든다). 켜려면 안내 NPC(그 층 NPC, 150601)와 문장 1개 이상(150603)이 필요하고, 문장은 NPC
 * 대사와 같은 2줄 규칙(150602). 미션 시작 버튼 문구는 비우면 앱 기본 문구. {@code reshow} = 이미 본 유저에게도 한 번 더 보여 준다(버전을 올린다).
 */
@JsonNaming(SnakeCaseStrategy.class)
public record TowerFloorGuideAdminRequest(
        @NotNull Boolean enabled,
        Long npcId,
        @Size(max = TowerFloorGuide.MAX_LINES) List<@Valid @NotNull TowerNpcLineDto> lines,
        Boolean showMissionButton,
        @Size(max = 30) String missionButtonLabel,
        @Size(max = 30) String missionButtonLabelEn,
        @Size(max = 30) String missionButtonLabelJa,
        @Size(max = 30) String missionButtonLabelAr,
        Boolean reshow) {

    public List<TowerNpcLineDto> safeLines() {
        return lines == null ? List.of() : lines;
    }

    public TowerFloorGuide toEntity(Long floorId) {
        return TowerFloorGuide.builder()
                .floorId(floorId)
                .enabled(enabled)
                .npcId(npcId)
                .showMissionButton(Boolean.TRUE.equals(showMissionButton))
                .missionButtonLabel(blankToNull(missionButtonLabel))
                .missionButtonLabelEn(blankToNull(missionButtonLabelEn))
                .missionButtonLabelJa(blankToNull(missionButtonLabelJa))
                .missionButtonLabelAr(blankToNull(missionButtonLabelAr))
                .lines(
                        new ArrayList<>(
                                safeLines().stream().map(TowerNpcLineDto::toEntity).toList()))
                .build();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
