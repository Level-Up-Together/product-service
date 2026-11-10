package io.pinkspider.leveluptogethermvp.towerservice.domain.entity;

import io.pinkspider.global.domain.auditentity.LocalDateTimeBaseEntity;
import io.pinkspider.leveluptogethermvp.towerservice.domain.enums.TowerNpcFormBodyType;
import io.pinkspider.leveluptogethermvp.towerservice.domain.enums.TowerNpcFormDisplayMode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Comment;

/**
 * LUT-760: NPC 입력 받기 설정 (NPC 당 1행). 켜면 NPC 카드 대사 아래에 입력칸이 열려 유저 글을 받는다. 어드민 설정만 바꿔 트리 쪽지(한 줄·비로그인
 * 허용·태그 공개)·보름달 소원(여러 줄·이메일·로그인만·떠오르는 글) 같은 이벤트를 만든다. 이메일을 받으면 로그인 유저만 받는다(서버가 강제).
 */
@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table(
        name = "tower_npc_form",
        uniqueConstraints =
                @UniqueConstraint(name = "uk_tower_npc_form_npc", columnNames = "npc_id"))
@Comment("레벨업 탑 NPC 입력 받기 설정")
public class TowerNpcForm extends LocalDateTimeBaseEntity {

    public static final int DEFAULT_MAX_LENGTH = 100;
    public static final int MAX_MAX_LENGTH = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "npc_id", nullable = false)
    private Long npcId;

    @Column(name = "form_enabled", nullable = false)
    @Builder.Default
    private Boolean enabled = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "body_type", nullable = false, length = 10)
    @Builder.Default
    private TowerNpcFormBodyType bodyType = TowerNpcFormBodyType.INPUT;

    @Column(name = "placeholder", length = 100)
    private String placeholder;

    @Column(name = "placeholder_en", length = 100)
    private String placeholderEn;

    @Column(name = "placeholder_ja", length = 100)
    private String placeholderJa;

    @Column(name = "placeholder_ar", length = 100)
    private String placeholderAr;

    @Column(name = "max_length", nullable = false)
    @Builder.Default
    private Integer maxLength = DEFAULT_MAX_LENGTH;

    @Column(name = "submit_label", length = 30)
    private String submitLabel;

    @Column(name = "submit_label_en", length = 30)
    private String submitLabelEn;

    @Column(name = "submit_label_ja", length = 30)
    private String submitLabelJa;

    @Column(name = "submit_label_ar", length = 30)
    private String submitLabelAr;

    @Column(name = "login_required", nullable = false)
    @Builder.Default
    private Boolean loginRequired = false;

    @Column(name = "email_enabled", nullable = false)
    @Builder.Default
    private Boolean emailEnabled = false;

    @Column(name = "consent_text", length = 200)
    private String consentText;

    @Column(name = "consent_text_en", length = 200)
    private String consentTextEn;

    @Column(name = "consent_text_ja", length = 200)
    private String consentTextJa;

    @Column(name = "consent_text_ar", length = 200)
    private String consentTextAr;

    @Enumerated(EnumType.STRING)
    @Column(name = "display_mode", nullable = false, length = 10)
    @Builder.Default
    private TowerNpcFormDisplayMode displayMode = TowerNpcFormDisplayMode.NONE;

    @Column(name = "display_mask_url", length = 500)
    private String displayMaskUrl;

    public boolean isEnabled() {
        return Boolean.TRUE.equals(enabled);
    }

    /** 이메일을 받으면 로그인 유저만 — 저장값과 무관하게 여기서도 한 번 더 강제한다 */
    public boolean isLoginRequired() {
        return Boolean.TRUE.equals(loginRequired) || isEmailEnabled();
    }

    public boolean isEmailEnabled() {
        return Boolean.TRUE.equals(emailEnabled);
    }

    /** 공개 조회 대상인가 — 켜져 있고 공개 방식이 NONE 이 아니다 */
    public boolean isDisplayed() {
        return isEnabled() && displayMode != null && displayMode != TowerNpcFormDisplayMode.NONE;
    }

    /** 통째로 바꾼다 (NPC 는 바꾸지 않는다) */
    public void update(TowerNpcForm source) {
        this.enabled = source.enabled;
        this.bodyType = source.bodyType;
        this.placeholder = source.placeholder;
        this.placeholderEn = source.placeholderEn;
        this.placeholderJa = source.placeholderJa;
        this.placeholderAr = source.placeholderAr;
        this.maxLength = source.maxLength;
        this.submitLabel = source.submitLabel;
        this.submitLabelEn = source.submitLabelEn;
        this.submitLabelJa = source.submitLabelJa;
        this.submitLabelAr = source.submitLabelAr;
        this.loginRequired = source.loginRequired;
        this.emailEnabled = source.emailEnabled;
        this.consentText = source.consentText;
        this.consentTextEn = source.consentTextEn;
        this.consentTextJa = source.consentTextJa;
        this.consentTextAr = source.consentTextAr;
        this.displayMode = source.displayMode;
        this.displayMaskUrl = source.displayMaskUrl;
    }
}
