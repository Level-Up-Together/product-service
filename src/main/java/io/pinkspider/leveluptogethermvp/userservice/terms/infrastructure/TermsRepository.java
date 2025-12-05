package io.pinkspider.leveluptogethermvp.userservice.terms.infrastructure;

import io.pinkspider.leveluptogethermvp.userservice.terms.domain.response.RecentTermsResponseDto;
import io.pinkspider.leveluptogethermvp.userservice.terms.domain.response.TermAgreementsByUserResponseDto;
import io.pinkspider.leveluptogethermvp.userservice.unit.user.domain.entity.Term;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface TermsRepository extends JpaRepository<Term, Long> {

    @Query(value = """
        WITH latest_versions AS (
            SELECT
                tv.*,
                ROW_NUMBER() OVER (PARTITION BY tv.term_id ORDER BY version::NUMERIC DESC) AS rn
            FROM term_versions tv
        )
        SELECT
            t.id AS termId,
            t.title AS termTitle,
            t.code,
            t.type,
            t.is_required AS isRequired,
            lv.id AS versionId,
            lv.version,
            lv.created_at AS createdAt,
            lv.content
        FROM terms t
        JOIN latest_versions lv ON t.id = lv.term_id AND lv.rn = 1;
        """, nativeQuery = true)
    List<RecentTermsResponseDto> getRecentAllTerms();

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
                 JOIN latest_term_versions ltv ON t.id = ltv.terms_id AND ltv.rn = 1
                 LEFT JOIN user_term_agreements uta
                           ON uta.term_version_id = ltv.id AND uta.user_id = :userId;
        """, nativeQuery = true)
    List<TermAgreementsByUserResponseDto> getTermAgreementsByUser(@Param("userId") String userId);
}
