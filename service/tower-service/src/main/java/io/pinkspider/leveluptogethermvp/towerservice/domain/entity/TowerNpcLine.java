package io.pinkspider.leveluptogethermvp.towerservice.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** LUT-693: NPC 대사 한 문장 (4개 locale). 순서는 {@code tower_npc_line.line_order}. */
@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class TowerNpcLine {

    @Column(name = "text", nullable = false, length = 200)
    private String text;

    @Column(name = "text_en", nullable = false, length = 200)
    private String textEn;

    @Column(name = "text_ja", nullable = false, length = 200)
    private String textJa;

    @Column(name = "text_ar", nullable = false, length = 200)
    private String textAr;
}
