package io.pinkspider.leveluptogethermvp.towerservice.domain.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.pinkspider.leveluptogethermvp.towerservice.domain.entity.TowerNpcCard;
import io.pinkspider.leveluptogethermvp.towerservice.domain.enums.TowerNpcCardLinkType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * LUT-733: NPC 를 눌렀을 때 뜨는 창의 이미지·이동 링크(어드민 요청·응답, 입장 스냅샷 공용). 이미지는 층 이미지 업로드(kind=NPC) 주소, 가로 2 :
 * 세로 1 권장. 버튼 문구는 비우면 클라이언트 기본 문구("바로 가기"). 기간(KST, 양끝 포함)은 비우면 상시.
 */
@JsonNaming(SnakeCaseStrategy.class)
public record TowerNpcCardDto(
        @Size(max = 500)
                @Pattern(regexp = TowerFloorAdminRequest.HTTPS_URL, message = "https URL 만 허용합니다")
                String imageUrl,
        @Valid TowerNpcCardLinkDto link,
        @Size(max = 30) String linkLabel,
        @Size(max = 30) String linkLabelEn,
        @Size(max = 30) String linkLabelJa,
        @Size(max = 30) String linkLabelAr,
        LocalDate startsOn,
        LocalDate endsOn) {

    /** 이미지도 링크도 없으면 null(창 묶음 없음). 빈 문구는 null */
    public TowerNpcCard toEntityOrNull() {
        String image = blankToNull(imageUrl);
        TowerNpcCardLinkType type = link != null ? link.type() : null;
        if (image == null && type == null) {
            return null;
        }
        boolean hasTarget =
                type == TowerNpcCardLinkType.SHOP_ITEM || type == TowerNpcCardLinkType.EVENT;
        return TowerNpcCard.builder()
                .imageUrl(image)
                .linkType(type)
                .linkTargetId(hasTarget ? link.targetId() : null)
                .linkPath(type == TowerNpcCardLinkType.PATH ? blankToNull(link.path()) : null)
                .linkLabel(type != null ? blankToNull(linkLabel) : null)
                .linkLabelEn(type != null ? blankToNull(linkLabelEn) : null)
                .linkLabelJa(type != null ? blankToNull(linkLabelJa) : null)
                .linkLabelAr(type != null ? blankToNull(linkLabelAr) : null)
                .startsOn(startsOn)
                .endsOn(endsOn)
                .build();
    }

    public static TowerNpcCardDto from(TowerNpcCard card) {
        if (card == null) {
            return null;
        }
        TowerNpcCardLinkDto link =
                card.hasLink()
                        ? new TowerNpcCardLinkDto(
                                card.getLinkType(), card.getLinkTargetId(), card.getLinkPath())
                        : null;
        return new TowerNpcCardDto(
                card.getImageUrl(),
                link,
                card.getLinkLabel(),
                card.getLinkLabelEn(),
                card.getLinkLabelJa(),
                card.getLinkLabelAr(),
                card.getStartsOn(),
                card.getEndsOn());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
