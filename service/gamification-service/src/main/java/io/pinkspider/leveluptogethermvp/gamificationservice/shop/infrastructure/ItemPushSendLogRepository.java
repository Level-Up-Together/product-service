package io.pinkspider.leveluptogethermvp.gamificationservice.shop.infrastructure;

import io.pinkspider.leveluptogethermvp.gamificationservice.shop.domain.entity.ItemPushSendLog;
import java.time.LocalDate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** LUT-516: 장착 아이템 푸시 발송 중복방지 원장 저장소 */
@Repository
public interface ItemPushSendLogRepository extends JpaRepository<ItemPushSendLog, Long> {

    /** 슬롯 단위 발송 여부 (fast-path 중복 체크). 최종 방어는 uk_item_push_send_slot 유니크 제약. */
    boolean existsByUserIdAndShopItemIdAndSendDateAndSendTime(
            String userId, Long shopItemId, LocalDate sendDate, String sendTime);
}
