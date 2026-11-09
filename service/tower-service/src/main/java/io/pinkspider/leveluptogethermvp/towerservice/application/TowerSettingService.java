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
 * LUT-635: 탑 공통 설정 — 저장 즉시 반영되도록 캐시 없이 읽는다.
 *
 * <ul>
 *   <li>동시 접속 상한(대기열 LUT-701 이 {@link #getMaxConcurrentUsers()} 로 판정). 값이 없으면 제한 없음.
 *   <li>LUT-693 NPC 노출 비율 0~100(A/B — {@link #getNpcExposurePercent()}). 값이 없으면 100(모두에게 보임).
 * </ul>
 */
@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(transactionManager = "towerTransactionManager", readOnly = true)
public class TowerSettingService {

    public static final String MAX_CONCURRENT_USERS = "max_concurrent_users";
    public static final String NPC_EXPOSURE_PERCENT = "npc_exposure_percent";
    public static final int DEFAULT_NPC_EXPOSURE_PERCENT = 100;

    private final TowerSettingRepository towerSettingRepository;

    public TowerSettingsResponse get() {
        return new TowerSettingsResponse(
                getMaxConcurrentUsers().orElse(null), getNpcExposurePercent());
    }

    /** 탑 전체 동시 접속 상한. 없거나 숫자가 아니면 empty(= 제한 없음). */
    public Optional<Integer> getMaxConcurrentUsers() {
        return read(MAX_CONCURRENT_USERS).filter(value -> value >= 1);
    }

    /** NPC 를 보여 줄 유저 비율(0~100). 없거나 범위 밖이면 100. */
    public int getNpcExposurePercent() {
        return read(NPC_EXPOSURE_PERCENT)
                .filter(value -> value >= 0 && value <= 100)
                .orElse(DEFAULT_NPC_EXPOSURE_PERCENT);
    }

    /** 동시 접속 상한 null = 제한 없음(행 삭제). NPC 노출 비율 null = 바꾸지 않음. */
    @Transactional(transactionManager = "towerTransactionManager")
    public TowerSettingsResponse update(TowerSettingsRequest request) {
        Integer maxUsers = request.maxConcurrentUsers();
        if (maxUsers == null) {
            towerSettingRepository.deleteById(MAX_CONCURRENT_USERS);
            log.info("탑 공통 설정: 동시 접속 상한 해제(제한 없음)");
        } else {
            write(MAX_CONCURRENT_USERS, maxUsers);
            log.info("탑 공통 설정: 동시 접속 상한={}", maxUsers);
        }
        Integer npcPercent = request.npcExposurePercent();
        if (npcPercent != null) {
            write(NPC_EXPOSURE_PERCENT, npcPercent);
            log.info("탑 공통 설정: NPC 노출 비율={}%", npcPercent);
        }
        return new TowerSettingsResponse(
                maxUsers, npcPercent != null ? npcPercent : getNpcExposurePercent());
    }

    private Optional<Integer> read(String key) {
        return towerSettingRepository
                .findById(key)
                .map(TowerSetting::getSettingValue)
                .flatMap(TowerSettingService::parseInt);
    }

    private void write(String key, int value) {
        TowerSetting setting =
                towerSettingRepository
                        .findById(key)
                        .orElseGet(
                                () ->
                                        TowerSetting.builder()
                                                .settingKey(key)
                                                .settingValue(String.valueOf(value))
                                                .build());
        setting.update(String.valueOf(value));
        towerSettingRepository.save(setting);
    }

    private static Optional<Integer> parseInt(String raw) {
        try {
            return Optional.of(Integer.parseInt(raw.trim()));
        } catch (NumberFormatException | NullPointerException e) {
            log.warn("탑 공통 설정 값이 숫자가 아님: {}", raw);
            return Optional.empty();
        }
    }
}
