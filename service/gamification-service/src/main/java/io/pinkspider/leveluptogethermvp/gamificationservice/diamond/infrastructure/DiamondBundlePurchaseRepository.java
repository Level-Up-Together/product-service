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
    default Page<DiamondPaymentHistoryRow> search(
        LocalDateTime startAt, LocalDateTime endAt, String platform, Long bundleId,
        DiamondPurchaseStatus status, Pageable pageable) {
        return searchInternal(
            startAt != null, startAt, endAt != null, endAt, platform != null, platform,
            bundleId != null, bundleId, status != null, status, pageable);
    }

    /**
     * LUT-431: {@code (:param IS NULL OR ...)} 패턴은 단독 {@code :param IS NULL} 자리의 타입을
     * PostgreSQL 이 추론하지 못해, 필터 미지정(null 바인드) 시 42P18(could not determine data type of
     * parameter)로 조회 전체가 죽는다 — timestamp/bigint/enum 파라미터가 대상이며 H2 기반 테스트에선
     * 재현되지 않아 dev/prod 에서만 드러났다. 필터 적용 여부를 boolean 플래그로 분리해 nullable
     * 파라미터가 항상 타입 추론이 가능한 비교 위치에만 나타나게 한다. 호출은 위 default 메서드로.
     */
    @Query(value = "SELECT new io.pinkspider.leveluptogethermvp.gamificationservice.diamond.domain.dto."
        + "DiamondPaymentHistoryRow(dbp.id, dbp.userId, dbp.bundleId, db.name, dbp.platform, "
        + "dbp.storeProductId, dbp.storeTransactionId, dbp.diamondCount, dbp.priceAmount, "
        + "dbp.priceCurrency, dbp.status, dbp.refundedAt, dbp.createdAt) "
        + "FROM DiamondBundlePurchase dbp LEFT JOIN DiamondBundle db ON db.id = dbp.bundleId "
        + "WHERE (:startAtSet = false OR dbp.createdAt >= :startAt) "
        + "AND (:endAtSet = false OR dbp.createdAt <= :endAt) "
        + "AND (:platformSet = false OR dbp.platform = :platform) "
        + "AND (:bundleIdSet = false OR dbp.bundleId = :bundleId) "
        + "AND (:statusSet = false OR dbp.status = :status) "
        + "ORDER BY dbp.id DESC",
        countQuery = "SELECT COUNT(dbp) FROM DiamondBundlePurchase dbp "
            + "WHERE (:startAtSet = false OR dbp.createdAt >= :startAt) "
            + "AND (:endAtSet = false OR dbp.createdAt <= :endAt) "
            + "AND (:platformSet = false OR dbp.platform = :platform) "
            + "AND (:bundleIdSet = false OR dbp.bundleId = :bundleId) "
            + "AND (:statusSet = false OR dbp.status = :status)")
    Page<DiamondPaymentHistoryRow> searchInternal(
        @Param("startAtSet") boolean startAtSet,
        @Param("startAt") LocalDateTime startAt,
        @Param("endAtSet") boolean endAtSet,
        @Param("endAt") LocalDateTime endAt,
        @Param("platformSet") boolean platformSet,
        @Param("platform") String platform,
        @Param("bundleIdSet") boolean bundleIdSet,
        @Param("bundleId") Long bundleId,
        @Param("statusSet") boolean statusSet,
        @Param("status") DiamondPurchaseStatus status,
        Pageable pageable);

    /** LUT-401: 어드민 결제이력 — 닉네임 매칭된 userId 목록으로 추가 필터링 (빈 리스트는 JPQL IN 무효라 별도 메서드로 분리) */
    default Page<DiamondPaymentHistoryRow> searchWithUsers(
        LocalDateTime startAt, LocalDateTime endAt, String platform, Long bundleId,
        DiamondPurchaseStatus status, List<String> userIds, Pageable pageable) {
        return searchWithUsersInternal(
            startAt != null, startAt, endAt != null, endAt, platform != null, platform,
            bundleId != null, bundleId, status != null, status, userIds, pageable);
    }

    /** LUT-431: 플래그 분리 이유는 {@link #searchInternal} 참조. 호출은 위 default 메서드로. */
    @Query(value = "SELECT new io.pinkspider.leveluptogethermvp.gamificationservice.diamond.domain.dto."
        + "DiamondPaymentHistoryRow(dbp.id, dbp.userId, dbp.bundleId, db.name, dbp.platform, "
        + "dbp.storeProductId, dbp.storeTransactionId, dbp.diamondCount, dbp.priceAmount, "
        + "dbp.priceCurrency, dbp.status, dbp.refundedAt, dbp.createdAt) "
        + "FROM DiamondBundlePurchase dbp LEFT JOIN DiamondBundle db ON db.id = dbp.bundleId "
        + "WHERE (:startAtSet = false OR dbp.createdAt >= :startAt) "
        + "AND (:endAtSet = false OR dbp.createdAt <= :endAt) "
        + "AND (:platformSet = false OR dbp.platform = :platform) "
        + "AND (:bundleIdSet = false OR dbp.bundleId = :bundleId) "
        + "AND (:statusSet = false OR dbp.status = :status) "
        + "AND dbp.userId IN :userIds "
        + "ORDER BY dbp.id DESC",
        countQuery = "SELECT COUNT(dbp) FROM DiamondBundlePurchase dbp "
            + "WHERE (:startAtSet = false OR dbp.createdAt >= :startAt) "
            + "AND (:endAtSet = false OR dbp.createdAt <= :endAt) "
            + "AND (:platformSet = false OR dbp.platform = :platform) "
            + "AND (:bundleIdSet = false OR dbp.bundleId = :bundleId) "
            + "AND (:statusSet = false OR dbp.status = :status) "
            + "AND dbp.userId IN :userIds")
    Page<DiamondPaymentHistoryRow> searchWithUsersInternal(
        @Param("startAtSet") boolean startAtSet,
        @Param("startAt") LocalDateTime startAt,
        @Param("endAtSet") boolean endAtSet,
        @Param("endAt") LocalDateTime endAt,
        @Param("platformSet") boolean platformSet,
        @Param("platform") String platform,
        @Param("bundleIdSet") boolean bundleIdSet,
        @Param("bundleId") Long bundleId,
        @Param("statusSet") boolean statusSet,
        @Param("status") DiamondPurchaseStatus status,
        @Param("userIds") List<String> userIds,
        Pageable pageable);
}
