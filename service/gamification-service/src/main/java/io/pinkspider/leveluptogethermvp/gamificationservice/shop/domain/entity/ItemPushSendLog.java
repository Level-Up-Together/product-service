package io.pinkspider.leveluptogethermvp.gamificationservice.shop.domain.entity;

import io.pinkspider.global.domain.auditentity.LocalDateTimeBaseEntity;
import io.pinkspider.leveluptogethermvp.gamificationservice.shop.domain.enums.ItemPushSlot;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * LUT-516/528/573: 장착 아이템 푸시 발송 원장. (user_id, send_date) 유니크로 유저·로컬날짜당 정확히 1회(하루 1건 상한)를 보장하고, 다중
 * 인스턴스/재실행을 방어한다.
 *
 * <p>LUT-573: 이 원장이 발송 간격(유저+아이템 마지막 발송일로부터 2일)·아침/오후 로테이션(마지막 발송 {@code slot})·이력 기반 대사 선택(유저+아이템별
 * 메시지 마지막 발송일)의 근거다. {@code slot} 은 발송된 슬롯(메시지 trigger_type 이 아님, ANY 폴백이어도 슬롯)이며, LUT-573 이전 행은
 * null — 간격·로테이션 판정에서는 제외돼 "첫 발송"으로 취급되고, 대사 선택 이력에는 포함된다(같은 문장 반복 방지에 유리).
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
                        name = "uk_item_push_send_user_date",
                        columnNames = {"user_id", "send_date"}),
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
    @Comment("발송 슬롯 시각 HH:mm (유저 로컬)")
    private String sendTime;

    @Enumerated(EnumType.STRING)
    @Column(name = "slot", length = 20)
    @Comment("발송된 슬롯 (BEFORE_ACTIVITY/INACTIVE/AFTER_COMPLETE, LUT-573 이전 행은 null)")
    private ItemPushSlot slot;

    public static ItemPushSendLog record(
            Long itemPushMessageId,
            Long shopItemId,
            String userId,
            LocalDate sendDate,
            ItemPushSlot slot,
            String sendTime) {
        return ItemPushSendLog.builder()
                .itemPushMessageId(itemPushMessageId)
                .shopItemId(shopItemId)
                .userId(userId)
                .sendDate(sendDate)
                .slot(slot)
                .sendTime(sendTime)
                .build();
    }
}
