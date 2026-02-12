package io.pinkspider.leveluptogethermvp.userservice.terms.infrastructure;

import io.pinkspider.leveluptogethermvp.userservice.unit.user.domain.entity.TermVersion;
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
}
