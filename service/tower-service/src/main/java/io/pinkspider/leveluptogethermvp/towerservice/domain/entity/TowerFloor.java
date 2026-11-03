package io.pinkspider.leveluptogethermvp.towerservice.domain.entity;

import io.pinkspider.global.domain.auditentity.LocalDateTimeBaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * LUT-629: 레벨업 탑의 층. 어드민이 등록하며 프론트는 이 표를 API 로 받아 그릴 뿐 아무것도 하드코딩하지 않는다.
 *
 * <p>층 번호와 입장 레벨은 별개다. 입장 레벨 {@code null} = 제한 없음(가장 낮은 값으로 친다). 저장 규칙 7개는 {@link
 * io.pinkspider.leveluptogethermvp.towerservice.application.TowerFloorRuleValidator} 가 지킨다.
 */
@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table(
        name = "tower_floor",
        uniqueConstraints =
                @UniqueConstraint(name = "uk_tower_floor_number", columnNames = "floor_number"))
@Comment("레벨업 탑 층")
public class TowerFloor extends LocalDateTimeBaseEntity {

    public static final int DEFAULT_INSTANCE_CAPACITY = 30;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "floor_number", nullable = false)
    @Comment("층 번호 (1부터, 유일)")
    private Integer floorNumber;

    @Column(name = "name", nullable = false, length = 100)
    @Comment("층 이름 (ko)")
    private String name;

    @Column(name = "name_en", nullable = false, length = 100)
    private String nameEn;

    @Column(name = "name_ja", nullable = false, length = 100)
    private String nameJa;

    @Column(name = "name_ar", nullable = false, length = 100)
    private String nameAr;

    @Column(name = "entry_level")
    @Comment("입장 레벨 (NULL = 제한 없음, 1층은 항상 NULL)")
    private Integer entryLevel;

    @Column(name = "guest_allowed", nullable = false)
    @Comment("비로그인 방문객 입장 허용")
    @Builder.Default
    private Boolean guestAllowed = false;

    @Column(name = "background_image_url", nullable = false, length = 500)
    private String backgroundImageUrl;

    @Column(name = "floor_mask_url", nullable = false, length = 500)
    @Comment("바닥 마스크 PNG (붉은색 = 이동 가능), 배경과 같은 좌표계")
    private String floorMaskUrl;

    @Column(name = "entry_point_x", nullable = false)
    @Comment("입장 지점 X (배경 원본 픽셀)")
    private Integer entryPointX;

    @Column(name = "entry_point_y", nullable = false)
    @Comment("입장 지점 Y (배경 원본 픽셀)")
    private Integer entryPointY;

    @Column(name = "bgm_url", length = 500)
    private String bgmUrl;

    @Column(name = "instance_capacity", nullable = false)
    @Comment("인스턴스(방) 상한, 기본 30")
    @Builder.Default
    private Integer instanceCapacity = DEFAULT_INSTANCE_CAPACITY;

    @Column(name = "is_public", nullable = false)
    @Comment("공개 여부 (비공개는 유저 목록에서 제외)")
    @Builder.Default
    private Boolean isPublic = false;

    /** 제한 없음(null)은 가장 낮은 값으로 친다 — 순서 비교용 */
    public int effectiveEntryLevel() {
        return entryLevel == null ? 0 : entryLevel;
    }

    public boolean isGuestAllowed() {
        return Boolean.TRUE.equals(guestAllowed);
    }

    public boolean isPublicFloor() {
        return Boolean.TRUE.equals(isPublic);
    }

    /** 어드민 수정 — 수정은 즉시 반영되고, 이미 안에 있는 유저는 다음 입장부터 새 조건을 받는다 */
    public void update(TowerFloor source) {
        this.floorNumber = source.floorNumber;
        this.name = source.name;
        this.nameEn = source.nameEn;
        this.nameJa = source.nameJa;
        this.nameAr = source.nameAr;
        this.entryLevel = source.entryLevel;
        this.guestAllowed = source.guestAllowed;
        this.backgroundImageUrl = source.backgroundImageUrl;
        this.floorMaskUrl = source.floorMaskUrl;
        this.entryPointX = source.entryPointX;
        this.entryPointY = source.entryPointY;
        this.bgmUrl = source.bgmUrl;
        this.instanceCapacity = source.instanceCapacity;
        this.isPublic = source.isPublic;
    }
}
