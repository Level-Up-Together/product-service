package io.pinkspider.leveluptogethermvp.towerservice.domain.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.pinkspider.leveluptogethermvp.towerservice.domain.entity.TowerNpcForm;
import io.pinkspider.leveluptogethermvp.towerservice.domain.enums.TowerNpcFormBodyType;
import io.pinkspider.leveluptogethermvp.towerservice.domain.enums.TowerNpcFormDisplayMode;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * LUT-760: 어드민 NPC 입력 받기 설정 (요청·응답 공용, NPC 요청의 {@code form}). 비운 값의 기본: 본문 칸 한 줄, 최대 100자, 공개 방식
 * 없음. 이메일 받기를 켜면 로그인 유저만 받기는 서버가 켬으로 강제한다. 켜진 설정은 버튼 문구 4개 언어가 필수이고, 이메일 받기면 동의 문구 4개 언어, 공개 방식을
 * 고르면 공개 영역 마스크(배경과 같은 비율)가 필수다(150408·150409).
 */
@JsonNaming(SnakeCaseStrategy.class)
public record TowerNpcFormDto(
        @NotNull Boolean formEnabled,
        TowerNpcFormBodyType bodyType,
        @Size(max = 100) String placeholder,
        @Size(max = 100) String placeholderEn,
        @Size(max = 100) String placeholderJa,
        @Size(max = 100) String placeholderAr,
        @Min(1) @Max(TowerNpcForm.MAX_MAX_LENGTH) Integer maxLength,
        @Size(max = 30) String submitLabel,
        @Size(max = 30) String submitLabelEn,
        @Size(max = 30) String submitLabelJa,
        @Size(max = 30) String submitLabelAr,
        Boolean loginRequired,
        Boolean emailEnabled,
        @Size(max = 200) String consentText,
        @Size(max = 200) String consentTextEn,
        @Size(max = 200) String consentTextJa,
        @Size(max = 200) String consentTextAr,
        TowerNpcFormDisplayMode displayMode,
        @Size(max = 500)
                @Pattern(regexp = TowerFloorAdminRequest.HTTPS_URL, message = "https URL 만 허용합니다")
                String displayMaskUrl) {

    /** Jackson 이 is* 를 속성으로 보지 않게 — 응답에는 record 필드만 나간다 */
    @JsonIgnore
    public boolean isEnabled() {
        return Boolean.TRUE.equals(formEnabled);
    }

    @JsonIgnore
    public boolean isEmailEnabled() {
        return Boolean.TRUE.equals(emailEnabled);
    }

    public TowerNpcFormDisplayMode displayModeOrNone() {
        return displayMode != null ? displayMode : TowerNpcFormDisplayMode.NONE;
    }

    public TowerNpcForm toEntity(Long npcId) {
        boolean email = isEmailEnabled();
        return TowerNpcForm.builder()
                .npcId(npcId)
                .enabled(isEnabled())
                .bodyType(bodyType != null ? bodyType : TowerNpcFormBodyType.INPUT)
                .placeholder(blankToNull(placeholder))
                .placeholderEn(blankToNull(placeholderEn))
                .placeholderJa(blankToNull(placeholderJa))
                .placeholderAr(blankToNull(placeholderAr))
                .maxLength(maxLength != null ? maxLength : TowerNpcForm.DEFAULT_MAX_LENGTH)
                .submitLabel(blankToNull(submitLabel))
                .submitLabelEn(blankToNull(submitLabelEn))
                .submitLabelJa(blankToNull(submitLabelJa))
                .submitLabelAr(blankToNull(submitLabelAr))
                // 이메일을 받으면 로그인 유저만 — 클라이언트 값과 무관하게 강제
                .loginRequired(email || Boolean.TRUE.equals(loginRequired))
                .emailEnabled(email)
                .consentText(email ? blankToNull(consentText) : null)
                .consentTextEn(email ? blankToNull(consentTextEn) : null)
                .consentTextJa(email ? blankToNull(consentTextJa) : null)
                .consentTextAr(email ? blankToNull(consentTextAr) : null)
                .displayMode(displayModeOrNone())
                .displayMaskUrl(blankToNull(displayMaskUrl))
                .build();
    }

    public static TowerNpcFormDto from(TowerNpcForm f) {
        if (f == null) {
            return null;
        }
        return new TowerNpcFormDto(
                f.isEnabled(),
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
                f.getDisplayMaskUrl());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
