package io.pinkspider.leveluptogethermvp.userservice.terms.infrastructure;

import io.pinkspider.leveluptogethermvp.userservice.unit.user.domain.entity.UserTermAgreement;
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
public interface UserTermAgreementsRepository extends JpaRepository<UserTermAgreement, Long> {

    @Query(value = """
        SELECT uta
        FROM UserTermAgreement uta
        WHERE uta.users.id = :userId
          AND uta.termVersion.id = :termVersionId
        """
    )
    Optional<UserTermAgreement> findAllByUserIdAndTermVersionId(@Param("userId") String userId, @Param("termVersionId") Long termVersionId);

    // ========== Admin Internal API 용 ==========

    @Query("SELECT uta FROM UserTermAgreement uta " +
           "JOIN FETCH uta.termVersion tv " +
           "JOIN FETCH tv.terms t " +
           "WHERE uta.users.id = :userId " +
           "ORDER BY t.id ASC, tv.id DESC")
    List<UserTermAgreement> findByUserIdWithTerms(@Param("userId") String userId);

    @Query("SELECT uta FROM UserTermAgreement uta " +
           "JOIN uta.termVersion tv " +
           "WHERE uta.users.id = :userId AND tv.terms.id = :termsId " +
           "ORDER BY tv.id DESC")
    List<UserTermAgreement> findByUserIdAndTermsId(@Param("userId") String userId, @Param("termsId") Long termsId);

    @Query("SELECT COUNT(uta) FROM UserTermAgreement uta " +
           "WHERE uta.termVersion.id = :termVersionId AND uta.isAgreed = true")
    Long countAgreedByTermVersionId(@Param("termVersionId") Long termVersionId);

    @Query("SELECT COUNT(DISTINCT uta.users.id) FROM UserTermAgreement uta " +
           "JOIN uta.termVersion tv " +
           "WHERE tv.terms.id = :termsId AND uta.isAgreed = true")
    Long countDistinctUsersByTermsIdAndAgreed(@Param("termsId") Long termsId);

    @Query(value = "SELECT uta FROM UserTermAgreement uta " +
           "JOIN FETCH uta.termVersion tv " +
           "JOIN FETCH tv.terms t " +
           "WHERE (:userId IS NULL OR uta.users.id LIKE %:userId%) " +
           "AND (:termsId IS NULL OR t.id = :termsId) " +
           "AND (:isAgreed IS NULL OR uta.isAgreed = :isAgreed)",
           countQuery = "SELECT COUNT(uta) FROM UserTermAgreement uta " +
           "JOIN uta.termVersion tv " +
           "JOIN tv.terms t " +
           "WHERE (:userId IS NULL OR uta.users.id LIKE %:userId%) " +
           "AND (:termsId IS NULL OR t.id = :termsId) " +
           "AND (:isAgreed IS NULL OR uta.isAgreed = :isAgreed)")
    Page<UserTermAgreement> searchAgreementsWithFetch(
        @Param("userId") String userId,
        @Param("termsId") Long termsId,
        @Param("isAgreed") Boolean isAgreed,
        Pageable pageable);

    @Query("SELECT DISTINCT uta.users.id FROM UserTermAgreement uta " +
           "WHERE (:keyword IS NULL OR uta.users.id LIKE %:keyword%) " +
           "ORDER BY uta.users.id ASC")
    Page<String> findDistinctUserIds(@Param("keyword") String keyword, Pageable pageable);

    @Query("SELECT COUNT(uta) FROM UserTermAgreement uta " +
           "WHERE uta.users.id = :userId")
    Long countByUsersId(@Param("userId") String userId);

    @Query("SELECT COUNT(uta) FROM UserTermAgreement uta " +
           "WHERE uta.users.id = :userId AND uta.isAgreed = true")
    Long countAgreedByUsersId(@Param("userId") String userId);

    @Query("SELECT COUNT(uta) FROM UserTermAgreement uta " +
           "JOIN uta.termVersion tv " +
           "JOIN tv.terms t " +
           "WHERE uta.users.id = :userId AND t.isRequired = true")
    Long countRequiredTermsByUsersId(@Param("userId") String userId);

    @Query("SELECT COUNT(uta) FROM UserTermAgreement uta " +
           "JOIN uta.termVersion tv " +
           "JOIN tv.terms t " +
           "WHERE uta.users.id = :userId AND t.isRequired = true AND uta.isAgreed = true")
    Long countRequiredAgreedByUsersId(@Param("userId") String userId);

    @Query("SELECT MAX(uta.agreedAt) FROM UserTermAgreement uta " +
           "WHERE uta.users.id = :userId AND uta.isAgreed = true")
    LocalDateTime findLastAgreedAtByUsersId(@Param("userId") String userId);
}
