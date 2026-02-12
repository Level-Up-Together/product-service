package io.pinkspider.leveluptogethermvp.userservice.terms.infrastructure;

import io.pinkspider.leveluptogethermvp.userservice.unit.user.domain.entity.UserTermAgreement;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface UserTermAgreementsRepository extends JpaRepository<UserTermAgreement, Long> {


    @Query(value = """
        SELECT uta
        FROM UserTermAgreement uta
        WHERE uta.users.id = :userId
          AND uta.termVersion.id = :termVersionId
        """
    )
    Optional<UserTermAgreement> findAllByUserIdAndTermVersionId(@Param("userId") String userId, @Param("termVersionId") Long termVersionId);
}
