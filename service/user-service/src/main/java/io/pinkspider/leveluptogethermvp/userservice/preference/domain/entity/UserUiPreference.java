package io.pinkspider.leveluptogethermvp.userservice.preference.domain.entity;

import io.pinkspider.global.domain.auditentity.LocalDateTimeBaseEntity;
import io.pinkspider.leveluptogethermvp.userservice.core.converter.LongListConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.Comment;

/**
 * LUT-437: 유저 UI 환경설정 (기기 간 동기화용).
 *
 * <p>알림 설정(notification_preference)과 달리 순수 화면 토글만 담는다. 앞으로 생길 다른 UI 토글도 이 테이블에 컬럼을 추가해 재사용한다.
 */
@Entity
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table(
        name = "user_ui_preference",
        indexes = @Index(name = "idx_user_ui_pref_user", columnList = "user_id", unique = true))
@Comment("유저 UI 환경설정")
public class UserUiPreference extends LocalDateTimeBaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    @Comment("ID")
    private Long id;

    @NotNull
    @Column(name = "user_id", nullable = false, unique = true)
    @Comment("사용자 ID")
    private String userId;

    @Column(name = "mission_completed_section_collapsed")
    @Comment("나의 미션 '오늘 완료한 미션' 섹션 접힘 여부")
    @lombok.Builder.Default
    private Boolean missionCompletedSectionCollapsed = false;

    /** LUT-548: 홈 피드 카테고리 필터 — 빈 목록이 "전체". 쉼표 구분 문자열 컬럼(비면 NULL). */
    @Convert(converter = LongListConverter.class)
    @Column(name = "home_feed_category_ids", length = 500)
    @Comment("홈 피드 카테고리 필터 (쉼표 구분 카테고리 ID, 비면 전체)")
    @lombok.Builder.Default
    private List<Long> homeFeedCategoryIds = new ArrayList<>();

    public static UserUiPreference createDefault(String userId) {
        return UserUiPreference.builder().userId(userId).build();
    }
}
