package io.pinkspider.leveluptogethermvp.userservice.unit.user.application;

import io.pinkspider.global.exception.CustomException;
import io.pinkspider.global.util.CryptoUtils;
import io.pinkspider.leveluptogethermvp.userservice.unit.user.domain.entity.Users;
import io.pinkspider.leveluptogethermvp.userservice.unit.user.infrastructure.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserRepository userRepository;

    /**
     * 이메일과 provider로 사용자 조회
     * @param email 평문 이메일 (내부에서 암호화하여 조회)
     * @param provider OAuth2 제공자
     * @return 사용자 엔티티
     */
    public Users findByEmailAndProvider(String email, String provider) {
        String encryptedEmail = CryptoUtils.encryptAes(email);
        return userRepository.findByEncryptedEmailAndProvider(encryptedEmail, provider)
            .orElseThrow(() -> new CustomException("404", "사용자를 찾을 수 없습니다."));
    }

    public Users findByUserId(String userId) {
        return userRepository.findById(userId)
            .orElseThrow(() -> new CustomException("", ""));
    }
}
