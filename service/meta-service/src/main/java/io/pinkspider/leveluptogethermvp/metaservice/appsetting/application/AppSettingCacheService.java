package io.pinkspider.leveluptogethermvp.metaservice.appsetting.application;

import io.pinkspider.global.exception.CustomException;
import io.pinkspider.leveluptogethermvp.metaservice.appsetting.domain.entity.AppSetting;
import io.pinkspider.leveluptogethermvp.metaservice.appsetting.infrastructure.AppSettingRepository;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * LUT-708: 어드민 서비스 설정({@code app_setting}) 읽기·쓰기 + Redis 캐시({@code appSettings}, TTL 없음 — 쓰기 때
 * evict).
 *
 * <p>스토어 인앱 평가 마일스톤: 연속 출석 일수가 목록의 값과 같을 때 웹이 OS 평가 팝업을 요청한다(LUT-609). "평가했는지" 는 OS 가 알려 주지 않으므로
 * 목록의 각 시점에 한 번씩 요청하고(7 → 14 → 30), 목록이 끝나면 더 요청하지 않는다. 캐시에는 엔티티가 아니라 파싱한 숫자 목록만 담는다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true, transactionManager = "metaTransactionManager")
public class AppSettingCacheService {

    public static final String CACHE_NAME = "appSettings";
    public static final String STORE_REVIEW_MILESTONE_DAYS = "store_review_milestone_days";
    public static final List<Integer> DEFAULT_STORE_REVIEW_MILESTONES = List.of(7, 14, 30);

    static final int MAX_MILESTONE_COUNT = 10;
    static final int MAX_MILESTONE_DAY = 365;
    static final String INVALID_MILESTONES_CODE = "080101";
    static final String INVALID_MILESTONES_MESSAGE = "error.app_setting.invalid_milestones";

    private final AppSettingRepository repository;

    /**
     * 평가 팝업을 요청할 연속 출석 일수 목록(오름차순). 행이 없으면 기본값 7·14·30, 빈 문자열이면 빈 목록(끔), 형식이 깨졌으면 기본값 + warn. 캐시 키:
     * appSettings::store_review_milestone_days
     */
    @Cacheable(value = CACHE_NAME, key = "'" + STORE_REVIEW_MILESTONE_DAYS + "'")
    public List<Integer> getStoreReviewMilestoneDays() {
        log.info("[AppSettingCacheService] DB에서 {} 로드 (캐시 미스)", STORE_REVIEW_MILESTONE_DAYS);
        return repository
                .findById(STORE_REVIEW_MILESTONE_DAYS)
                .map(setting -> parseMilestones(setting.getSettingValue()))
                .orElseGet(() -> new ArrayList<>(DEFAULT_STORE_REVIEW_MILESTONES));
    }

    /** 어드민 변경 — 검증(1~365, 최대 10개) 후 정렬·중복 제거해 저장하고 캐시를 비운다. 빈 목록은 "끔" 으로 저장한다 */
    @CacheEvict(value = CACHE_NAME, allEntries = true)
    @Transactional(transactionManager = "metaTransactionManager")
    public List<Integer> updateStoreReviewMilestoneDays(Collection<Integer> milestoneDays) {
        List<Integer> normalized = normalize(milestoneDays);
        String value = normalized.stream().map(String::valueOf).collect(Collectors.joining(","));
        repository
                .findById(STORE_REVIEW_MILESTONE_DAYS)
                .ifPresentOrElse(
                        setting -> setting.updateValue(value),
                        () ->
                                repository.save(
                                        AppSetting.builder()
                                                .settingKey(STORE_REVIEW_MILESTONE_DAYS)
                                                .settingValue(value)
                                                .description("스토어 인앱 평가 팝업 마일스톤(연속 출석 일수)")
                                                .build()));
        log.info("[AppSettingCacheService] {} 변경: {}", STORE_REVIEW_MILESTONE_DAYS, value);
        return normalized;
    }

    /** 검증 + 정렬 + 중복 제거. null 원소·범위 밖·개수 초과는 080101 */
    static List<Integer> normalize(Collection<Integer> milestoneDays) {
        if (milestoneDays == null) {
            throw new CustomException(INVALID_MILESTONES_CODE, INVALID_MILESTONES_MESSAGE);
        }
        List<Integer> normalized =
                milestoneDays.stream()
                        .map(
                                day -> {
                                    if (day == null || day < 1 || day > MAX_MILESTONE_DAY) {
                                        throw new CustomException(
                                                INVALID_MILESTONES_CODE,
                                                INVALID_MILESTONES_MESSAGE);
                                    }
                                    return day;
                                })
                        .distinct()
                        .sorted()
                        .collect(Collectors.toCollection(ArrayList::new));
        if (normalized.size() > MAX_MILESTONE_COUNT) {
            throw new CustomException(INVALID_MILESTONES_CODE, INVALID_MILESTONES_MESSAGE);
        }
        return normalized;
    }

    /** 저장 문자열 → 목록. 빈 문자열 = 끔(빈 목록). 숫자가 아니거나 범위 밖이면 기본값으로 폴백(warn) — 출석 체크를 막지 않기 위해 */
    static List<Integer> parseMilestones(String raw) {
        if (raw == null) {
            return new ArrayList<>(DEFAULT_STORE_REVIEW_MILESTONES);
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return new ArrayList<>();
        }
        try {
            List<Integer> parsed = new ArrayList<>();
            for (String token : trimmed.split(",")) {
                String t = token.trim();
                if (t.isEmpty()) {
                    continue;
                }
                parsed.add(Integer.parseInt(t));
            }
            return normalize(parsed);
        } catch (NumberFormatException | CustomException e) {
            log.warn(
                    "[AppSettingCacheService] {} 값이 올바르지 않아 기본값 사용: value='{}'",
                    STORE_REVIEW_MILESTONE_DAYS,
                    raw);
            return new ArrayList<>(DEFAULT_STORE_REVIEW_MILESTONES);
        }
    }
}
