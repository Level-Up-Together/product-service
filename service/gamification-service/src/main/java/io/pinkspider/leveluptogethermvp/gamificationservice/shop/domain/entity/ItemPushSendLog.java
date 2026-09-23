package io.pinkspider.leveluptogethermvp.gamificationservice.shop.domain.entity;

import io.pinkspider.global.domain.auditentity.LocalDateTimeBaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.Comment;

/**
 * LUT-516: 장착 아이템 푸시 발송 중복방지 원장. (user_id, shop_item_id, send_date, send_time) 유니크로 유저·아이템·로컬날짜·슬롯당
 * 정확히 1회 발송을 보장한다 — 같은 슬롯에 여러 메시지가 있어 랜덤 1개를 골라도 슬롯 단위로 잠기므로 이중 발송이 없다. 다중 인스턴스/재실행 방어.
 */
@Entity
@Getter
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table(
        name = "item_push_send_log",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uk_item_push_send_slot",
                        columnNames = {"user_id", "shop_item_id", "send_date", "send_time"}),
        indexes = @Index(name = "idx_item_push_send_log_send_date", columnList = "send_date"))
@Comment("장착 아이템 푸시 발송 중복방지 원장")
public class ItemPushSendLog extends LocalDateTimeBaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    @Comment("ID")
    private Long id;

    @NotNull
    @Column(name = "item_push_message_id", nullable = false)
    @Comment("실제 발송된(랜덤 선택된) 메시지 ID (감사용)")
    private Long itemPushMessageId;

    @NotNull
    @Column(name = "shop_item_id", nullable = false)
    @Comment("대상 아이템 ID")
    private Long shopItemId;

    @NotNull
    @Column(name = "user_id", nullable = false)
    @Comment("수신 유저 ID")
    private String userId;

    @NotNull
    @Column(name = "send_date", nullable = false)
    @Comment("유저 로컬 날짜")
    private LocalDate sendDate;

    @NotNull
    @Column(name = "send_time", nullable = false, length = 5)
    @Comment("발송 슬롯 HH:mm (유저 로컬)")
    private String sendTime;

    public static ItemPushSendLog record(
            Long itemPushMessageId,
            Long shopItemId,
            String userId,
            LocalDate sendDate,
            String sendTime) {
        return ItemPushSendLog.builder()
                .itemPushMessageId(itemPushMessageId)
                .shopItemId(shopItemId)
                .userId(userId)
                .sendDate(sendDate)
                .sendTime(sendTime)
                .build();
    }
}
