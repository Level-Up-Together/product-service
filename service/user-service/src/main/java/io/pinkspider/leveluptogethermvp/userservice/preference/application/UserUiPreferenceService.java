package io.pinkspider.leveluptogethermvp.userservice.preference.application;

import io.pinkspider.leveluptogethermvp.metaservice.application.MissionCategoryService;
import io.pinkspider.leveluptogethermvp.metaservice.domain.dto.MissionCategoryResponse;
import io.pinkspider.leveluptogethermvp.userservice.preference.domain.dto.UserUiPreferenceRequest;
import io.pinkspider.leveluptogethermvp.userservice.preference.domain.dto.UserUiPreferenceResponse;
import io.pinkspider.leveluptogethermvp.userservice.preference.domain.entity.UserUiPreference;
import io.pinkspider.leveluptogethermvp.userservice.preference.infrastructure.UserUiPreferenceRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
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
    private final MissionCategoryService missionCategoryService;

    /** 조회는 행을 만들지 않는다 — 나의 미션 진입마다 호출되므로 순수 읽기 유지 */
    public UserUiPreferenceResponse getPreferences(String userId) {
        return preferenceRepository
                .findByUserId(userId)
                .map(UserUiPreferenceResponse::from)
                .orElseGet(UserUiPreferenceResponse::defaults);
    }

    /** 부분 업데이트 — null 필드는 변경하지 않는다 (알림 설정과 동일 방식) */
    @Transactional(transactionManager = "userTransactionManager")
    public UserUiPreferenceResponse updatePreferences(
            String userId, UserUiPreferenceRequest request) {
        UserUiPreference preference =
                preferenceRepository
                        .findByUserId(userId)
                        .orElseGet(
                                () ->
                                        preferenceRepository.save(
                                                UserUiPreference.createDefault(userId)));

        if (request.getMissionCompletedSectionCollapsed() != null) {
            preference.setMissionCompletedSectionCollapsed(
                    request.getMissionCompletedSectionCollapsed());
        }

        // LUT-548: 홈 피드 카테고리 필터 — 빈 배열은 "전체"(필터 해제)로 저장
        if (request.getHomeFeedCategoryIds() != null) {
            preference.setHomeFeedCategoryIds(
                    sanitizeCategoryIds(request.getHomeFeedCategoryIds()));
        }

        log.info("UI 환경설정 수정: userId={}", userId);
        return UserUiPreferenceResponse.from(preference);
    }

    /**
     * 존재하지 않는 카테고리 ID 는 400 이 아니라 조용히 걸러낸다 — 클라이언트의 카테고리 목록 캐시(30분)가 낡아 삭제된 ID 를 보낼 수 있다. 비활성 카테고리는
     * 저장하고 적용 시점에 클라이언트가 걸러낸다. 카테고리 조회 자체가 실패하면 필터링 없이 요청값(중복 제거)을 저장한다 — 저장이 메타 DB 장애에 끌려가지 않게 한다.
     */
    private List<Long> sanitizeCategoryIds(List<Long> requested) {
        List<Long> distinct =
                requested.stream().filter(Objects::nonNull).distinct().collect(Collectors.toList());
        if (distinct.isEmpty()) {
            return new ArrayList<>();
        }
        try {
            Set<Long> existing =
                    missionCategoryService.getCategoriesByIds(distinct).stream()
                            .map(MissionCategoryResponse::getId)
                            .collect(Collectors.toSet());
            return distinct.stream()
                    .filter(existing::contains)
                    .collect(Collectors.toCollection(ArrayList::new));
        } catch (Exception e) {
            log.warn("홈 피드 카테고리 검증 실패 — 요청값 그대로 저장: error={}", e.getMessage());
            return new ArrayList<>(distinct);
        }
    }
}
