package io.pinkspider.leveluptogethermvp.towerservice.domain.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.pinkspider.leveluptogethermvp.towerservice.domain.entity.TowerNpcLine;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** LUT-693: NPC 대사 한 문장 (4개 locale). 길이는 2줄 이내 — {@code TowerNpcTextRule} 이 표시 폭으로 판정한다. */
@JsonNaming(SnakeCaseStrategy.class)
public record TowerNpcLineDto(
        @NotBlank @Size(max = 200) String text,
        @NotBlank @Size(max = 200) String textEn,
        @NotBlank @Size(max = 200) String textJa,
        @NotBlank @Size(max = 200) String textAr) {

    public TowerNpcLine toEntity() {
        return new TowerNpcLine(text.trim(), textEn.trim(), textJa.trim(), textAr.trim());
    }

    public static TowerNpcLineDto from(TowerNpcLine line) {
        return new TowerNpcLineDto(
                line.getText(), line.getTextEn(), line.getTextJa(), line.getTextAr());
    }
}
