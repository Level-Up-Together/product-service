package io.pinkspider.leveluptogethermvp.userservice.terms.infrastructure;

import io.pinkspider.leveluptogethermvp.userservice.unit.user.domain.entity.Term;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface TermsRepository extends JpaRepository<Term, Long> {

    /**
     * 최근 약관 목록 조회 (Object[] 반환으로 TupleBackedMap 버그 방지)
     * 컬럼 순서: term_id, term_title, code, type, is_required, version_id, version, created_at, content
     */
    @Query(value = """
        WITH latest_versions AS (
            SELECT
                tv.*,
                ROW_NUMBER() OVER (PARTITION BY tv.term_id ORDER BY version::NUMERIC DESC) AS rn
            FROM term_versions tv
        )
        SELECT
            t.id AS term_id,
            t.title AS term_title,
            t.code,
            t.type,
            t.is_required,
            lv.id AS version_id,
            lv.version,
            lv.created_at,
            lv.content
        FROM terms t
        JOIN latest_versions lv ON t.id = lv.term_id AND lv.rn = 1;
        """, nativeQuery = true)
    List<Object[]> getRecentAllTermsRaw();

    /**
     * 사용자의 약관 동의 상태 조회 (Object[] 반환으로 TupleBackedMap 버그 방지)
     * 컬럼 순서: term_id, term_title, is_required, latest_version_id, version, is_agreed, agreed_at
     */
    @Query(value = """
        WITH latest_term_versions AS (SELECT tv.*,
                                             ROW_NUMBER() OVER (PARTITION BY tv.term_id ORDER BY tv.created_at DESC) AS rn
                                      FROM term_versions tv)
        SELECT t.id    AS term_id,
               t.title AS term_title,
               t.is_required,
               ltv.id  AS latest_version_id,
               ltv.version,
               uta.is_agreed,
               uta.agreed_at
        FROM terms t
                 JOIN latest_term_versions ltv ON t.id = ltv.term_id AND ltv.rn = 1
                 LEFT JOIN user_term_agreements uta
                           ON uta.term_version_id = ltv.id AND uta.user_id = :userId;
        """, nativeQuery = true)
    List<Object[]> getTermAgreementsByUserRaw(@Param("userId") String userId);

    /**
     * 사용자가 아직 동의하지 않은 최신 버전 약관 조회 (Object[] 반환으로 TupleBackedMap 버그 방지)
     * 컬럼 순서: term_id, term_title, is_required, latest_version_id, version, is_agreed, agreed_at
     */
    @Query(value = """
        WITH latest_term_versions AS (
            SELECT tv.*,
                   ROW_NUMBER() OVER (PARTITION BY tv.term_id ORDER BY tv.created_at DESC) AS rn
            FROM term_versions tv
        )
        SELECT t.id    AS term_id,
               t.title AS term_title,
               t.is_required,
               ltv.id  AS latest_version_id,
               ltv.version,
               COALESCE(uta.is_agreed, false) AS is_agreed,
               uta.agreed_at
        FROM terms t
                 JOIN latest_term_versions ltv ON t.id = ltv.term_id AND ltv.rn = 1
                 LEFT JOIN user_term_agreements uta
                           ON uta.term_version_id = ltv.id AND uta.user_id = :userId
        WHERE uta.id IS NULL OR uta.is_agreed = false;
        """, nativeQuery = true)
    List<Object[]> getPendingTermsByUserRaw(@Param("userId") String userId);

    // ========== Admin Internal API 용 ==========

    List<Term> findAllByOrderByIdDesc();

    Optional<Term> findByCode(String code);

    boolean existsByCode(String code);

    List<Term> findByIsRequiredTrueOrderByIdAsc();

    List<Term> findByTypeOrderByIdAsc(String type);

    @Query("SELECT DISTINCT t.type FROM Term t WHERE t.type IS NOT NULL ORDER BY t.type")
    List<String> findAllTypes();

    @Query("SELECT t FROM Term t LEFT JOIN FETCH t.termVersions WHERE t.id = :id")
    Optional<Term> findByIdWithVersions(@Param("id") Long id);

    @Query(value = "SELECT t FROM Term t WHERE " +
           "(:keyword IS NULL OR :keyword = '' OR LOWER(t.title) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "OR LOWER(t.code) LIKE LOWER(CONCAT('%', :keyword, '%')))",
           countQuery = "SELECT COUNT(t) FROM Term t WHERE " +
           "(:keyword IS NULL OR :keyword = '' OR LOWER(t.title) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "OR LOWER(t.code) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    Page<Term> searchByKeyword(@Param("keyword") String keyword, Pageable pageable);
}
