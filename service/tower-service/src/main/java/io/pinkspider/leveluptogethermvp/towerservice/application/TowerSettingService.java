package io.pinkspider.leveluptogethermvp.towerservice.application;

import io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerSettingsRequest;
import io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerSettingsResponse;
import io.pinkspider.leveluptogethermvp.towerservice.domain.entity.TowerSetting;
import io.pinkspider.leveluptogethermvp.towerservice.infrastructure.TowerSettingRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * LUT-635: 탑 공통 설정. 지금은 동시 접속 상한 1개 — 저장 즉시 반영되도록 캐시 없이 읽는다(대기열 LUT-701 이 {@link
 * #getMaxConcurrentUsers()} 로 판정). 값이 없으면 제한 없음.
 */
@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(transactionManager = "towerTransactionManager", readOnly = true)
public class TowerSettingService {
    public static final String MAX_CONCURRENT_USERS = "max_concurrent_users";

    private final TowerSettingRepository towerSettingRepository;

    public TowerSettingsResponse get() {
        return new TowerSettingsResponse(getMaxConcurrentUsers().orElse(null));
    }

    /** 탑 전체 동시 접속 상한. 없거나 숫자가 아니면 empty(= 제한 없음). */
    public Optional<Integer> getMaxConcurrentUsers() {
        return towerSettingRepository
                .findById(MAX_CONCURRENT_USERS)
                .map(TowerSetting::getSettingValue)
                .flatMap(TowerSettingService::parsePositive);
    }

    @Transactional(transactionManager = "towerTransactionManager")
    public TowerSettingsResponse update(TowerSettingsRequest request) {
        Integer value = request.maxConcurrentUsers();
        if (value == null) {
            towerSettingRepository.deleteById(MAX_CONCURRENT_USERS);
            log.info("탑 공통 설정: 동시 접속 상한 해제(제한 없음)");
            return new TowerSettingsResponse(null);
        }
        TowerSetting setting =
                towerSettingRepository
                        .findById(MAX_CONCURRENT_USERS)
                        .orElseGet(
                                () ->
                                        TowerSetting.builder()
                                                .settingKey(MAX_CONCURRENT_USERS)
                                                .settingValue(String.valueOf(value))
                                                .build());
        setting.update(String.valueOf(value));
        towerSettingRepository.save(setting);
        log.info("탑 공통 설정: 동시 접속 상한={}", value);
        return new TowerSettingsResponse(value);
    }

    private static Optional<Integer> parsePositive(String raw) {
        try {
            int parsed = Integer.parseInt(raw.trim());
            return parsed >= 1 ? Optional.of(parsed) : Optional.empty();
        } catch (NumberFormatException e) {
            log.warn("탑 공통 설정 값이 숫자가 아님: {}", raw);
            return Optional.empty();
        }
    }
}
