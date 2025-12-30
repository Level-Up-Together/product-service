package io.pinkspider.leveluptogethermvp.userservice.unit.user.infrastructure;

import io.pinkspider.leveluptogethermvp.userservice.unit.user.domain.entity.Users;
import java.util.List;
import java.util.Optional;
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
     * 암호화된 이메일과 provider로 사용자 조회 (중복 가입 확인용)
     * 이메일은 호출 전에 CryptoUtils.encryptAes()로 암호화해야 함
     * provider는 대소문자 무시 (LOWER 함수 사용)
     */
    @Query(value = "SELECT * FROM users WHERE email = :encryptedEmail AND LOWER(provider) = LOWER(:provider)", nativeQuery = true)
    Optional<Users> findByEncryptedEmailAndProvider(
        @Param("encryptedEmail") String encryptedEmail,
        @Param("provider") String provider
    );

    List<Users> findAllByIdIn(List<String> userIds);

    // 닉네임 중복 확인 (자신 제외)
    boolean existsByNicknameAndIdNot(String nickname, String userId);

    // 닉네임 존재 여부 확인
    boolean existsByNickname(String nickname);

    // 닉네임으로 사용자 조회
    Optional<Users> findByNickname(String nickname);
}
