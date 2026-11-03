package io.pinkspider.leveluptogethermvp.gamificationservice.shop.domain.entity;

import io.pinkspider.global.domain.auditentity.LocalDateTimeBaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.Comment;

/**
 * LUT-528/573: 장착 아이템 푸시 발송 설정 (아이템 단위 1행, HEAD 타입만 — 서비스에서 검증). {@code shop_item.id} 를 그대로 PK 로 써서
 * 아이템과 1:1.
 *
 * <p>LUT-573: 발송 시각이 상태별 슬롯 2개로 나뉜다 — 아침 {@code before_activity_time}(08:00~11:59, BEFORE_ACTIVITY
 * 대사)과 오후 {@code after_complete_time}(12:00~20:59, AFTER_COMPLETE 대사). 정오 12:00 INACTIVE 슬롯은 고정이라
 * 설정이 없다. 둘 중 하나만 설정해도 되고(비면 그 슬롯은 발송 안 함), <b>행이 없거나 두 시각이 모두 null 이면 정오 슬롯을 포함해 아이템 푸시 전체가
 * 미발송</b>이다.
 */
@Entity
@Getter
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table(name = "item_push_setting")
@Comment("장착 아이템 푸시 발송 설정 (HEAD 타입만, 아이템당 1행)")
public class ItemPushSetting extends LocalDateTimeBaseEntity {

    @Id
    @Column(name = "shop_item_id", nullable = false)
    @Comment("대상 아이템 ID (shop_item.id, PK)")
    private Long shopItemId;

    @Column(name = "before_activity_time", length = 5)
    @Comment("아침(활동 전) 슬롯 HH:mm — 유저 로컬, 08:00~11:59. null 이면 아침 슬롯 미발송 (LUT-573)")
    private String beforeActivityTime;

    @Column(name = "after_complete_time", length = 5)
    @Comment("오후(완료 후) 슬롯 HH:mm — 유저 로컬, 12:00~20:59. null 이면 오후 슬롯 미발송 (LUT-573)")
    private String afterCompleteTime;

    @Column(name = "created_by")
    @Comment("등록 어드민 ID")
    private Long createdBy;

    public static ItemPushSetting of(
            Long shopItemId, String beforeActivityTime, String afterCompleteTime, Long createdBy) {
        return ItemPushSetting.builder()
                .shopItemId(shopItemId)
                .beforeActivityTime(beforeActivityTime)
                .afterCompleteTime(afterCompleteTime)
                .createdBy(createdBy)
                .build();
    }

    public void update(String beforeActivityTime, String afterCompleteTime) {
        this.beforeActivityTime = beforeActivityTime;
        this.afterCompleteTime = afterCompleteTime;
    }

    /** 두 시각이 모두 null 이면 정오 INACTIVE 슬롯을 포함해 아무것도 발송하지 않는다 (LUT-573). */
    public boolean hasAnySlot() {
        return beforeActivityTime != null || afterCompleteTime != null;
    }
}
