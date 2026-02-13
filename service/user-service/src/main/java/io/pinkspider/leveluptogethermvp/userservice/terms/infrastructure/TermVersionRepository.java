package io.pinkspider.leveluptogethermvp.userservice.terms.infrastructure;

import io.pinkspider.leveluptogethermvp.userservice.unit.user.domain.entity.TermVersion;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface TermVersionRepository extends JpaRepository<TermVersion, Long> {

    @Query("""
        SELECT tv
        FROM TermVersion tv
        WHERE tv.terms.id = :termId
        """)
    Optional<TermVersion> findByTermId(@Param("termId") Long termId);

    // ========== Admin Internal API 용 ==========

    List<TermVersion> findByTermsIdOrderByIdDesc(Long termsId);

    Optional<TermVersion> findTopByTermsIdOrderByIdDesc(Long termsId);

    boolean existsByTermsIdAndVersion(Long termsId, String version);

    @Query("SELECT tv FROM TermVersion tv JOIN FETCH tv.terms WHERE tv.id = :id")
    Optional<TermVersion> findByIdWithTerms(@Param("id") Long id);
}
