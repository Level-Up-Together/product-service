package io.pinkspider.leveluptogethermvp.gamificationservice.shop.domain.entity;

import io.pinkspider.global.domain.auditentity.LocalDateTimeBaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.Comment;

/**
 * LUT-516: HEAD 아이템 장착 유저에게 발송하는 개별 푸시 메시지 정의. 어드민이 아이템별로 메시지(4개 언어)와 발송 시각(HH:mm, 유저 로컬)을
 * 등록한다. 실제 발송은 {@code ItemPushScheduler} 가 담당하고, 이 엔티티는 정의만 보관한다.
 */
@Entity
@Getter
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table(
        name = "item_push_message",
        indexes = {
            @Index(name = "idx_item_push_message_shop_item", columnList = "shop_item_id"),
            @Index(name = "idx_item_push_message_enabled", columnList = "enabled")
        })
@Comment("장착 아이템 개별 푸시 메시지 정의 (HEAD 타입만)")
public class ItemPushMessage extends LocalDateTimeBaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    @Comment("ID")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shop_item_id", nullable = false)
    @Comment("대상 아이템 (HEAD 타입만)")
    private ShopItem shopItem;

    @NotNull
    @Column(name = "message", nullable = false, length = 500)
    @Comment("기본(ko) 문구, {nickname} 치환 토큰 허용")
    private String message;

    @Column(name = "message_en", length = 500)
    @Comment("영어 문구")
    private String messageEn;

    @Column(name = "message_ar", length = 500)
    @Comment("아랍어 문구")
    private String messageAr;

    @Column(name = "message_ja", length = 500)
    @Comment("일본어 문구")
    private String messageJa;

    @NotNull
    @Column(name = "send_time", nullable = false, length = 5)
    @Comment("발송 시각 HH:mm (유저 로컬 기준)")
    private String sendTime;

    @NotNull
    @Column(name = "enabled", nullable = false)
    @Comment("활성 여부 (끄면 발송 제외)")
    @Builder.Default
    private Boolean enabled = true;

    @Column(name = "created_by")
    @Comment("등록 어드민 ID")
    private Long createdBy;

    public static ItemPushMessage create(
            ShopItem shopItem,
            String message,
            String messageEn,
            String messageAr,
            String messageJa,
            String sendTime,
            Boolean enabled,
            Long createdBy) {
        return ItemPushMessage.builder()
                .shopItem(shopItem)
                .message(message)
                .messageEn(messageEn)
                .messageAr(messageAr)
                .messageJa(messageJa)
                .sendTime(sendTime)
                .enabled(enabled == null || enabled)
                .createdBy(createdBy)
                .build();
    }

    public void update(
            String message, String messageEn, String messageAr, String messageJa, String sendTime) {
        this.message = message;
        this.messageEn = messageEn;
        this.messageAr = messageAr;
        this.messageJa = messageJa;
        this.sendTime = sendTime;
    }

    public void changeEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void toggle() {
        this.enabled = !Boolean.TRUE.equals(this.enabled);
    }
}
