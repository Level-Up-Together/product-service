package io.pinkspider.leveluptogethermvp.userservice.unit.user.application;

import io.pinkspider.global.exception.CustomException;
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

    public Users findByEmailAndProvider(String email, String provider) {
        return userRepository.findByEmailAndProvider(email, provider)
            .orElseThrow(() -> new CustomException("", ""));
    }

    public Users findByUserId(String userId) {
        return userRepository.findById(userId)
            .orElseThrow(() -> new CustomException("", ""));
    }
}
