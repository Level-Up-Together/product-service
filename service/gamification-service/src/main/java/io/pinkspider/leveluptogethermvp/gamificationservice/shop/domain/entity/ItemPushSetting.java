package io.pinkspider.leveluptogethermvp.gamificationservice.shop.domain.entity;

import io.pinkspider.global.domain.auditentity.LocalDateTimeBaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.Comment;

/**
 * LUT-528: 장착 아이템 푸시 발송 설정. 발송 시각을 메시지 단위가 아니라 아이템 단위로 1개만 둔다. 행이 존재하면 그 아이템이 지정 시각에 발송되고, 행이 없으면
 * 발송하지 않는다. HEAD 타입 아이템에만 등록을 허용한다(서비스에서 검증). {@code shop_item.id} 를 그대로 PK 로 써서 아이템과 1:1.
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

    @NotNull
    @Column(name = "send_time", nullable = false, length = 5)
    @Comment("발송 시각 HH:mm (유저 로컬 기준, 08:00~20:59)")
    private String sendTime;

    @Column(name = "created_by")
    @Comment("등록 어드민 ID")
    private Long createdBy;

    public static ItemPushSetting of(Long shopItemId, String sendTime, Long createdBy) {
        return ItemPushSetting.builder()
                .shopItemId(shopItemId)
                .sendTime(sendTime)
                .createdBy(createdBy)
                .build();
    }

    public void updateSendTime(String sendTime) {
        this.sendTime = sendTime;
    }
}
