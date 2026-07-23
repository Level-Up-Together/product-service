package io.pinkspider.leveluptogethermvp.userservice.unit.user.infrastructure;

import io.pinkspider.leveluptogethermvp.userservice.unit.user.domain.entity.Users;
import io.pinkspider.leveluptogethermvp.userservice.unit.user.domain.enums.UserStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<Users, String> {

    /**
     * @deprecated JPA @Convert가 쿼리 파라미터에 적용되지 않아 암호화된 이메일 조회 실패.
     * {@link #findByEncryptedEmailAndProvider(String, String)} 사용 권장.
     */
    @Deprecated
    Optional<Users> findByEmailAndProvider(String email, String provider);

    /**
     * 탈퇴 이력 조회 (cool-down 판정용). 재가입→재탈퇴 반복으로 WITHDRAWN row 가 여러 개일 수 있어
     * List 로 반환하며, 최신 탈퇴가 첫 번째로 오도록 정렬한다. (LUT-258)
     */
    @Query(value = "SELECT * FROM users WHERE email = :encryptedEmail AND LOWER(provider) = LOWER(:provider) "
        + "AND status = 'WITHDRAWN' ORDER BY withdrawn_at DESC NULLS LAST, created_at DESC", nativeQuery = true)
    List<Users> findWithdrawnByEncryptedEmailAndProvider(
        @Param("encryptedEmail") String encryptedEmail,
        @Param("provider") String provider
    );

    /**
     * QA-115: cool-down 기반 재가입 정책에서, 활성 사용자(WITHDRAWN 제외)만 조회한다.
     * completeSignup 중복 체크에서 사용해 WITHDRAWN row 가 있어도 새 가입이 허용되도록 한다.
     */
    @Query(value = "SELECT * FROM users WHERE email = :encryptedEmail AND LOWER(provider) = LOWER(:provider) AND status <> 'WITHDRAWN'", nativeQuery = true)
    Optional<Users> findActiveByEncryptedEmailAndProvider(
        @Param("encryptedEmail") String encryptedEmail,
        @Param("provider") String provider
    );

    List<Users> findAllByIdIn(List<String> userIds);

    // 닉네임 중복 확인 (자신 제외)
    boolean existsByNicknameAndIdNot(String nickname, String userId);

    // 사용자 존재 여부 확인 (특정 상태 제외)
    boolean existsByIdAndStatusNot(String id, UserStatus status);

    // 닉네임 존재 여부 확인
    boolean existsByNickname(String nickname);

    // 닉네임으로 사용자 조회
    Optional<Users> findByNickname(String nickname);

    /**
     * 닉네임으로 사용자 검색 (닉네임이 설정된 활성 사용자만)
     */
    @Query("SELECT u FROM Users u WHERE u.nicknameSet = true " +
           "AND u.status = 'ACTIVE' " +
           "AND LOWER(u.nickname) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    Page<Users> searchByNickname(@Param("keyword") String keyword, Pageable pageable);

    /**
     * 주어진 사용자 ID 목록 중 활성(ACTIVE) 상태인 사용자 ID만 반환
     */
    @Query("SELECT u.id FROM Users u WHERE u.id IN :userIds AND u.status = 'ACTIVE'")
    List<String> findActiveUserIds(@Param("userIds") List<String> userIds);

    // ========== Admin Internal API 전용 ==========

    @Query("SELECT u FROM Users u WHERE " +
        "(:keyword IS NULL OR u.nickname LIKE %:keyword%) AND " +
        "(:provider IS NULL OR u.provider = :provider)")
    Page<Users> searchUsersForAdmin(
        @Param("keyword") String keyword,
        @Param("provider") String provider,
        Pageable pageable);

    /**
     * 이메일 단독 조회 (어드민용). 동일 이메일 row 가 여러 개일 수 있으므로(타 provider, 탈퇴 후 재가입)
     * 활성 계정 우선 + 최신 가입 순으로 1건만 반환한다. (LUT-258)
     */
    @Query(value = "SELECT * FROM users WHERE email = :encryptedEmail "
        + "ORDER BY CASE WHEN status = 'WITHDRAWN' THEN 1 ELSE 0 END, created_at DESC LIMIT 1",
        nativeQuery = true)
    Optional<Users> findByEncryptedEmail(@Param("encryptedEmail") String encryptedEmail);

    @Query("SELECT COUNT(u) FROM Users u WHERE u.createdAt >= :date")
    long countNewUsersSince(@Param("date") LocalDateTime date);

    @Query("SELECT u.provider, COUNT(u) FROM Users u GROUP BY u.provider")
    List<Object[]> countUsersByProvider();

    @Query("SELECT FUNCTION('DATE', u.createdAt), COUNT(u) FROM Users u " +
        "WHERE u.createdAt BETWEEN :start AND :end " +
        "GROUP BY FUNCTION('DATE', u.createdAt) ORDER BY FUNCTION('DATE', u.createdAt)")
    List<Object[]> countDailyNewUsers(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
}
