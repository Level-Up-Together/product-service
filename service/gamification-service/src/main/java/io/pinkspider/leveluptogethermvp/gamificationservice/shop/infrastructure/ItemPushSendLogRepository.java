package io.pinkspider.leveluptogethermvp.gamificationservice.shop.infrastructure;

import io.pinkspider.leveluptogethermvp.gamificationservice.shop.domain.entity.ItemPushSendLog;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.domain.Pageable;
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
     * LUT-529: 유저가 최근 발송받은 메시지 ID 목록 (send_date 내림차순). 대사 로테이션에서 최근 (후보 수 - 1)건을 제외하기 위해 사용한다.
     * (user_id, send_date) 유니크라 하루 1건 → 최근 N일 = 최근 N건.
     */
    @Query("SELECT l.itemPushMessageId FROM ItemPushSendLog l WHERE l.userId = :userId ORDER BY l.sendDate DESC")
    List<Long> findRecentSentMessageIds(@Param("userId") String userId, Pageable pageable);
}
