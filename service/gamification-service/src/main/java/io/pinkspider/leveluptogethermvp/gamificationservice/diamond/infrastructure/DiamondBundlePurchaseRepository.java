package io.pinkspider.leveluptogethermvp.gamificationservice.diamond.infrastructure;

import io.pinkspider.leveluptogethermvp.gamificationservice.diamond.domain.dto.DiamondPaymentHistoryRow;
import io.pinkspider.leveluptogethermvp.gamificationservice.diamond.domain.entity.DiamondBundlePurchase;
import io.pinkspider.leveluptogethermvp.gamificationservice.diamond.domain.enums.DiamondPurchaseStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface DiamondBundlePurchaseRepository extends JpaRepository<DiamondBundlePurchase, Long> {

    Optional<DiamondBundlePurchase> findByStoreTransactionId(String storeTransactionId);

    /** LUT-404: 번들 삭제 가드 — 결제 기록(재무 증적)이 있는 번들은 하드 삭제를 거부한다. */
    boolean existsByBundleId(Long bundleId);

    /**
     * LUT-401: 어드민 결제이력 — 닉네임 필터 없음(기간/플랫폼/상품/상태만). keyword 미입력이거나
     * 닉네임 매칭 결과가 없을 때 사용한다(닉네임 매칭 시엔 {@link #searchWithUsers}).
     *
     * <p>번들 조인은 LEFT — 가드(LUT-404) 이전에 삭제된 번들의 결제 기록도 목록에 남아야
     * count 쿼리(무조인)와 행수가 일치한다. 상품명만 null 로 내려간다.
     */
    @Query(value = "SELECT new io.pinkspider.leveluptogethermvp.gamificationservice.diamond.domain.dto."
        + "DiamondPaymentHistoryRow(dbp.id, dbp.userId, dbp.bundleId, db.name, dbp.platform, "
        + "dbp.storeProductId, dbp.storeTransactionId, dbp.diamondCount, dbp.priceAmount, "
        + "dbp.priceCurrency, dbp.status, dbp.refundedAt, dbp.createdAt) "
        + "FROM DiamondBundlePurchase dbp LEFT JOIN DiamondBundle db ON db.id = dbp.bundleId "
        + "WHERE (:startAt IS NULL OR dbp.createdAt >= :startAt) "
        + "AND (:endAt IS NULL OR dbp.createdAt <= :endAt) "
        + "AND (:platform IS NULL OR dbp.platform = :platform) "
        + "AND (:bundleId IS NULL OR dbp.bundleId = :bundleId) "
        + "AND (:status IS NULL OR dbp.status = :status) "
        + "ORDER BY dbp.id DESC",
        countQuery = "SELECT COUNT(dbp) FROM DiamondBundlePurchase dbp "
            + "WHERE (:startAt IS NULL OR dbp.createdAt >= :startAt) "
            + "AND (:endAt IS NULL OR dbp.createdAt <= :endAt) "
            + "AND (:platform IS NULL OR dbp.platform = :platform) "
            + "AND (:bundleId IS NULL OR dbp.bundleId = :bundleId) "
            + "AND (:status IS NULL OR dbp.status = :status)")
    Page<DiamondPaymentHistoryRow> search(
        @Param("startAt") LocalDateTime startAt,
        @Param("endAt") LocalDateTime endAt,
        @Param("platform") String platform,
        @Param("bundleId") Long bundleId,
        @Param("status") DiamondPurchaseStatus status,
        Pageable pageable);

    /** LUT-401: 어드민 결제이력 — 닉네임 매칭된 userId 목록으로 추가 필터링 (빈 리스트는 JPQL IN 무효라 별도 메서드로 분리) */
    @Query(value = "SELECT new io.pinkspider.leveluptogethermvp.gamificationservice.diamond.domain.dto."
        + "DiamondPaymentHistoryRow(dbp.id, dbp.userId, dbp.bundleId, db.name, dbp.platform, "
        + "dbp.storeProductId, dbp.storeTransactionId, dbp.diamondCount, dbp.priceAmount, "
        + "dbp.priceCurrency, dbp.status, dbp.refundedAt, dbp.createdAt) "
        + "FROM DiamondBundlePurchase dbp LEFT JOIN DiamondBundle db ON db.id = dbp.bundleId "
        + "WHERE (:startAt IS NULL OR dbp.createdAt >= :startAt) "
        + "AND (:endAt IS NULL OR dbp.createdAt <= :endAt) "
        + "AND (:platform IS NULL OR dbp.platform = :platform) "
        + "AND (:bundleId IS NULL OR dbp.bundleId = :bundleId) "
        + "AND (:status IS NULL OR dbp.status = :status) "
        + "AND dbp.userId IN :userIds "
        + "ORDER BY dbp.id DESC",
        countQuery = "SELECT COUNT(dbp) FROM DiamondBundlePurchase dbp "
            + "WHERE (:startAt IS NULL OR dbp.createdAt >= :startAt) "
            + "AND (:endAt IS NULL OR dbp.createdAt <= :endAt) "
            + "AND (:platform IS NULL OR dbp.platform = :platform) "
            + "AND (:bundleId IS NULL OR dbp.bundleId = :bundleId) "
            + "AND (:status IS NULL OR dbp.status = :status) "
            + "AND dbp.userId IN :userIds")
    Page<DiamondPaymentHistoryRow> searchWithUsers(
        @Param("startAt") LocalDateTime startAt,
        @Param("endAt") LocalDateTime endAt,
        @Param("platform") String platform,
        @Param("bundleId") Long bundleId,
        @Param("status") DiamondPurchaseStatus status,
        @Param("userIds") List<String> userIds,
        Pageable pageable);
}
