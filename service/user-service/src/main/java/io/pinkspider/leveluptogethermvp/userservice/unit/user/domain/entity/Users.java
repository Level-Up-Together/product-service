package io.pinkspider.leveluptogethermvp.userservice.unit.user.domain.entity;

import io.pinkspider.global.converter.CryptoConverter;
import io.pinkspider.global.domain.auditentity.LocalDateTimeBaseEntity;
import io.pinkspider.leveluptogethermvp.userservice.unit.user.domain.enums.UserStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Set;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.UuidGenerator;


@Entity
@Getter
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table(name = "users")
public class Users extends LocalDateTimeBaseEntity {

    @Id
    @UuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    private String id;

    @Column(name = "nickname", length = 50)
    private String nickname;

    @Convert(converter = CryptoConverter.class)
    @NotNull
    private String email;

    @Column
    private String picture;

    @NotNull
    private String provider;

    @lombok.Builder.Default
    @Column(name = "nickname_set", nullable = false)
    private boolean nicknameSet = false;

    @Column(name = "bio", length = 200)
    private String bio;

    @lombok.Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private UserStatus status = UserStatus.ACTIVE;

    @Column(name = "last_login_ip", length = 45)
    private String lastLoginIp;

    @Column(name = "last_login_country", length = 50)
    private String lastLoginCountry;

    @Column(name = "last_login_country_code", length = 2)
    private String lastLoginCountryCode;

    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;

    @lombok.Builder.Default
    @Column(name = "preferred_locale", length = 5, nullable = false)
    private String preferredLocale = "en";

    @lombok.Builder.Default
    @Column(name = "preferred_timezone", length = 50, nullable = false)
    private String preferredTimezone = "Asia/Seoul";

    @lombok.Builder.Default
    @Column(name = "preferred_feed_visibility", length = 20, nullable = false)
    private String preferredFeedVisibility = "PUBLIC";

    /**
     * 신고 처리로 정지된 누적 횟수. 영구강퇴 자동 전환 임계값 판정에 사용.
     */
    @lombok.Builder.Default
    @Column(name = "suspension_count", nullable = false)
    private Integer suspensionCount = 0;

    /**
     * 신고 처리로 받은 경고 누적 횟수. 자동 정지 임계값 판정에 사용.
     */
    @lombok.Builder.Default
    @Column(name = "warning_count", nullable = false)
    private Integer warningCount = 0;

    @Setter
    @OneToMany(mappedBy = "users")
    private Set<UserTermAgreement> userTermAgreements = new LinkedHashSet<>();

    public Users update(String nickname, String picture) {
        this.nickname = nickname;
        this.picture = picture;

        return this;
    }

    public void updatePicture(String picture) {
        this.picture = picture;
    }

    public void updateNickname(String nickname) {
        this.nickname = nickname;
        this.nicknameSet = true;
    }

    public void updateBio(String bio) {
        this.bio = bio;
    }

    public void updatePreferredLocale(String preferredLocale) {
        this.preferredLocale = preferredLocale;
    }

    public void updatePreferredTimezone(String preferredTimezone) {
        this.preferredTimezone = preferredTimezone;
    }

    public void updatePreferredFeedVisibility(String preferredFeedVisibility) {
        this.preferredFeedVisibility = preferredFeedVisibility;
    }

    public void updateLastLoginInfo(String ip, String country, String countryCode) {
        this.lastLoginIp = ip;
        this.lastLoginCountry = country;
        this.lastLoginCountryCode = countryCode;
        this.lastLoginAt = LocalDateTime.now();
    }

    public boolean isNicknameSet() {
        return nicknameSet;
    }

    /**
     * 상태 변경 (Admin용)
     */
    public void updateStatus(UserStatus status) {
        this.status = status;
    }

    /**
     * 정지 카운트 증가 후 새 카운트 반환 (신고 처리 후크용)
     */
    public int incrementSuspensionCount() {
        if (this.suspensionCount == null) {
            this.suspensionCount = 0;
        }
        this.suspensionCount += 1;
        return this.suspensionCount;
    }

    /**
     * 경고 카운트 증가 후 새 카운트 반환 (신고 처리 후크용)
     */
    public int incrementWarningCount() {
        if (this.warningCount == null) {
            this.warningCount = 0;
        }
        this.warningCount += 1;
        return this.warningCount;
    }

    /**
     * 회원 탈퇴 처리
     */
    public void withdraw() {
        this.status = UserStatus.WITHDRAWN;
        this.nickname = "탈퇴한 사용자";
        this.picture = null;
        this.bio = null;
    }

    /**
     * 표시용 이름 반환 (닉네임 > 이메일 앞부분)
     */
    public String getDisplayName() {
        if (nickname != null && !nickname.isBlank()) {
            return nickname;
        }
        // 이메일의 @ 앞부분 반환
        if (email != null && email.contains("@")) {
            return email.substring(0, email.indexOf("@"));
        }
        return "사용자";
    }
}
