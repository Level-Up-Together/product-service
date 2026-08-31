package io.pinkspider.leveluptogethermvp.userservice.preference.application;

import io.pinkspider.leveluptogethermvp.userservice.preference.domain.dto.UserUiPreferenceRequest;
import io.pinkspider.leveluptogethermvp.userservice.preference.domain.dto.UserUiPreferenceResponse;
import io.pinkspider.leveluptogethermvp.userservice.preference.domain.entity.UserUiPreference;
import io.pinkspider.leveluptogethermvp.userservice.preference.infrastructure.UserUiPreferenceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(transactionManager = "userTransactionManager", readOnly = true)
public class UserUiPreferenceService {

    private final UserUiPreferenceRepository preferenceRepository;

    /** 조회는 행을 만들지 않는다 — 나의 미션 진입마다 호출되므로 순수 읽기 유지 */
    public UserUiPreferenceResponse getPreferences(String userId) {
        return preferenceRepository.findByUserId(userId)
            .map(UserUiPreferenceResponse::from)
            .orElseGet(UserUiPreferenceResponse::defaults);
    }

    /** 부분 업데이트 — null 필드는 변경하지 않는다 (알림 설정과 동일 방식) */
    @Transactional(transactionManager = "userTransactionManager")
    public UserUiPreferenceResponse updatePreferences(String userId, UserUiPreferenceRequest request) {
        UserUiPreference preference = preferenceRepository.findByUserId(userId)
            .orElseGet(() -> preferenceRepository.save(UserUiPreference.createDefault(userId)));

        if (request.getMissionCompletedSectionCollapsed() != null) {
            preference.setMissionCompletedSectionCollapsed(
                request.getMissionCompletedSectionCollapsed());
        }

        log.info("UI 환경설정 수정: userId={}", userId);
        return UserUiPreferenceResponse.from(preference);
    }
}
