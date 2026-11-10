package io.pinkspider.leveluptogethermvp.towerservice.domain.entity;

import io.pinkspider.leveluptogethermvp.towerservice.domain.enums.TowerNpcCardLinkType;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * LUT-733: NPC 를 눌렀을 때 뜨는 창의 이미지·이동 링크. 이미지나 링크 중 하나는 있다(둘 다 없으면 저장하지 않는다). 기간은 KST 날짜, 양끝 포함, 비우면
 * 상시.
 */
@Embeddable
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class TowerNpcCard {

    @Column(name = "card_image_url", length = 500)
    private String imageUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "card_link_type", length = 20)
    private TowerNpcCardLinkType linkType;

    @Column(name = "card_link_target_id")
    private Long linkTargetId;

    @Column(name = "card_link_path", length = 300)
    private String linkPath;

    @Column(name = "card_link_label", length = 30)
    private String linkLabel;

    @Column(name = "card_link_label_en", length = 30)
    private String linkLabelEn;

    @Column(name = "card_link_label_ja", length = 30)
    private String linkLabelJa;

    @Column(name = "card_link_label_ar", length = 30)
    private String linkLabelAr;

    @Column(name = "card_starts_on")
    private LocalDate startsOn;

    @Column(name = "card_ends_on")
    private LocalDate endsOn;

    public boolean hasLink() {
        return linkType != null;
    }

    /** 오늘(KST)이 노출 기간 안인가 — 비운 쪽은 열려 있다 */
    public boolean isActiveOn(LocalDate today) {
        return (startsOn == null || !today.isBefore(startsOn))
                && (endsOn == null || !today.isAfter(endsOn));
    }
}
