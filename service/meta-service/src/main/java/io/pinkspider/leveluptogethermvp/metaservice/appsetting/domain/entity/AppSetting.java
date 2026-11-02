package io.pinkspider.leveluptogethermvp.metaservice.appsetting.domain.entity;

import io.pinkspider.global.domain.auditentity.LocalDateTimeBaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Comment;

/**
 * LUT-708: 어드민이 바꾸는 서비스 설정 키-값. 값은 문자열이며 해석(파싱·검증·기본값)은 {@code AppSettingCacheService} 가 맡는다. 첫 키는
 * {@code store_review_milestone_days}(스토어 인앱 평가 팝업을 요청할 연속 출석 일수 목록).
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "app_setting")
@Comment("어드민 서비스 설정 키-값")
public class AppSetting extends LocalDateTimeBaseEntity {

    @Id
    @Column(name = "setting_key", length = 100, nullable = false)
    @Comment("설정 키")
    private String settingKey;

    @Column(name = "setting_value", nullable = false, length = 500)
    @Comment("설정 값(문자열)")
    private String settingValue;

    @Column(name = "description", length = 300)
    @Comment("설명")
    private String description;

    @Builder
    public AppSetting(String settingKey, String settingValue, String description) {
        this.settingKey = settingKey;
        this.settingValue = settingValue;
        this.description = description;
    }

    public void updateValue(String settingValue) {
        this.settingValue = settingValue;
    }
}
