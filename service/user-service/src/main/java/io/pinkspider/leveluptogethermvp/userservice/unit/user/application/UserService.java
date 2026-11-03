package io.pinkspider.leveluptogethermvp.userservice.unit.user.application;

import io.pinkspider.global.exception.CustomException;
import io.pinkspider.global.util.CryptoUtils;
import io.pinkspider.leveluptogethermvp.userservice.unit.user.domain.entity.Users;
import io.pinkspider.leveluptogethermvp.userservice.unit.user.infrastructure.UserRepository;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserRepository userRepository;

    /**
     * 이메일과 provider로 사용자 조회
     *
     * @param email 평문 이메일 (내부에서 암호화하여 조회)
     * @param provider OAuth2 제공자
     * @return 사용자 엔티티
     */
    public Users findByEmailAndProvider(String email, String provider) {
        String encryptedEmail = CryptoUtils.encryptAes(email);
        return userRepository
                .findActiveByEncryptedEmailAndProvider(encryptedEmail, provider)
                .orElseThrow(() -> new CustomException("404", "error.user.not_found"));
    }

    public Users findByUserId(String userId) {
        return userRepository.findById(userId).orElseThrow(() -> new CustomException("", ""));
    }

    /**
     * 오늘 가입한 신규 유저인지 확인.
     *
     * <p>LUT-597: 출석 "가입 첫날 제외" 규칙이 폐지되어 product 안에 호출처가 없다. kernel {@code UserQueryFacade} 인터페이스
     * 호환을 위해 유지한다. UTC 날짜 비교(JVM 기본 UTC)라 유저 타임존 기준 "오늘"과 다를 수 있다.
     */
    @Transactional(transactionManager = "userTransactionManager", readOnly = true)
    public boolean isNewUserToday(String userId) {
        return userRepository
                .findById(userId)
                .map(
                        user ->
                                user.getCreatedAt() != null
                                        && user.getCreatedAt()
                                                .toLocalDate()
                                                .equals(LocalDate.now()))
                .orElse(false);
    }
}
