package io.pinkspider.leveluptogethermvp.gamificationservice.shop.infrastructure;

import io.pinkspider.leveluptogethermvp.gamificationservice.shop.domain.entity.ItemPushSendLog;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/** LUT-516: 장착 아이템 푸시 발송 중복방지 원장 저장소 */
@Repository
public interface ItemPushSendLogRepository extends JpaRepository<ItemPushSendLog, Long> {

    /** 유저·로컬일자 발송 여부 (fast-path 중복 체크). 최종 방어는 uk_item_push_send_user_date 유니크 제약. (LUT-528) */
    boolean existsByUserIdAndSendDate(String userId, LocalDate sendDate);

    /**
     * LUT-573: 유저+아이템의 마지막 발송 행 (간격·로테이션 판정). LUT-573 이전 행(slot null)은 제외해 마이그레이션 전 이력이 있던 유저도 "첫
     * 발송"으로 취급한다.
     */
    Optional<ItemPushSendLog> findTopByUserIdAndShopItemIdAndSlotIsNotNullOrderBySendDateDesc(
            String userId, Long shopItemId);

    /**
     * LUT-573: 유저+아이템에서 메시지별 마지막 발송일 — 이력 기반 대사 선택(미발송 우선 → 가장 오래전 발송). 삭제된 메시지 id 도 그대로 남지만 후보에
     * 없으니 자연히 제외된다. 레거시 행(slot null)도 포함한다.
     */
    @Query(
            "SELECT l.itemPushMessageId, MAX(l.sendDate) FROM ItemPushSendLog l"
                    + " WHERE l.userId = :userId AND l.shopItemId = :shopItemId"
                    + " GROUP BY l.itemPushMessageId")
    List<Object[]> findLastSentDateByMessage(
            @Param("userId") String userId, @Param("shopItemId") Long shopItemId);
}
