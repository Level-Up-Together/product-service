package io.pinkspider.leveluptogethermvp.towerservice.domain.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.pinkspider.leveluptogethermvp.towerservice.domain.entity.TowerNpcForm;
import io.pinkspider.leveluptogethermvp.towerservice.domain.enums.TowerNpcFormBodyType;
import io.pinkspider.leveluptogethermvp.towerservice.domain.enums.TowerNpcFormDisplayMode;

/**
 * LUT-760: 입장 스냅샷 {@code npcs[].form} — 켜진 설정만 내린다(꺼졌거나 없으면 {@code form: null}, 구 클라이언트는 무시). 문구는
 * 4개 locale 을 다 내리고 클라이언트가 고른다. {@code entry_count} = 공개 글 수(숨김 제외, 시드 포함) — "쪽지 128개" 표시용.
 */
@JsonNaming(SnakeCaseStrategy.class)
public record TowerNpcFormView(
        TowerNpcFormBodyType bodyType,
        String placeholder,
        String placeholderEn,
        String placeholderJa,
        String placeholderAr,
        Integer maxLength,
        String submitLabel,
        String submitLabelEn,
        String submitLabelJa,
        String submitLabelAr,
        Boolean loginRequired,
        Boolean emailEnabled,
        String consentText,
        String consentTextEn,
        String consentTextJa,
        String consentTextAr,
        TowerNpcFormDisplayMode displayMode,
        String displayMaskUrl,
        Long entryCount) {

    public static TowerNpcFormView from(TowerNpcForm f, long entryCount) {
        return new TowerNpcFormView(
                f.getBodyType(),
                f.getPlaceholder(),
                f.getPlaceholderEn(),
                f.getPlaceholderJa(),
                f.getPlaceholderAr(),
                f.getMaxLength(),
                f.getSubmitLabel(),
                f.getSubmitLabelEn(),
                f.getSubmitLabelJa(),
                f.getSubmitLabelAr(),
                f.isLoginRequired(),
                f.isEmailEnabled(),
                f.getConsentText(),
                f.getConsentTextEn(),
                f.getConsentTextJa(),
                f.getConsentTextAr(),
                f.getDisplayMode(),
                f.getDisplayMaskUrl(),
                entryCount);
    }
}
