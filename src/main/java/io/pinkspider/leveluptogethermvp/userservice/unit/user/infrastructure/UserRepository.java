package io.pinkspider.leveluptogethermvp.userservice.unit.user.infrastructure;

import io.pinkspider.leveluptogethermvp.userservice.unit.user.domain.entity.Users;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<Users, String> {

    Optional<Users> findByEmailAndProvider(String email, String provider); // 중복 가입 확인

    List<Users> findAllByIdIn(List<String> userIds);

    // 닉네임 중복 확인 (자신 제외)
    boolean existsByNicknameAndIdNot(String nickname, String userId);

    // 닉네임 존재 여부 확인
    boolean existsByNickname(String nickname);

    // 닉네임으로 사용자 조회
    Optional<Users> findByNickname(String nickname);
}
