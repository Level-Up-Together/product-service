package io.pinkspider.leveluptogethermvp.statsservice.activity.application;

import io.pinkspider.leveluptogethermvp.statsservice.activity.domain.UserActivityKeys;
import io.pinkspider.leveluptogethermvp.statsservice.activity.domain.entity.UserDailyActivity;
import io.pinkspider.leveluptogethermvp.statsservice.activity.infrastructure.UserDailyActivityRepository;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * LUT-598: Redis 활동 집합 → {@code user_daily_activity} 적재.
 *
 * <p>(user_id, activity_date) 유니크라 같은 날짜를 몇 번 flush 해도 결과가 같다(멱등). 이미 있는 행은 기기 종류만 합친다. Redis 키는
 * 지우지 않는다(TTL 자연 소멸).
 */
@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(transactionManager = "statsTransactionManager", readOnly = true)
public class UserActivityFlushService {

    private final StringRedisTemplate redisTemplate;
    private final UserDailyActivityRepository repository;

    /**
     * @return 새로 넣었거나 갱신한 행 수
     */
    @Transactional(transactionManager = "statsTransactionManager")
    public int flush(LocalDate date) {
        Set<String> userIds = members(UserActivityKeys.dailyKey(date));
        if (userIds.isEmpty()) {
            log.info("활동 flush: date={} 활동 유저 없음", date);
            return 0;
        }

        Map<String, Set<String>> devicesByUser = new HashMap<>();
        for (String deviceType : UserActivityKeys.DEVICE_TYPES) {
            for (String userId : members(UserActivityKeys.deviceKey(date, deviceType))) {
                devicesByUser.computeIfAbsent(userId, k -> new TreeSet<>()).add(deviceType);
            }
        }

        Map<String, UserDailyActivity> existing =
                repository.findAllByActivityDate(date).stream()
                        .collect(
                                Collectors.toMap(
                                        UserDailyActivity::getUserId, Function.identity()));

        List<UserDailyActivity> toSave = new ArrayList<>();
        for (String userId : userIds) {
            Set<String> devices = devicesByUser.getOrDefault(userId, Set.of());
            UserDailyActivity row = existing.get(userId);
            if (row == null) {
                toSave.add(UserDailyActivity.create(userId, date, devices));
            } else if (row.mergeDeviceTypes(devices)) {
                toSave.add(row);
            }
        }
        repository.saveAll(toSave);
        log.info(
                "활동 flush 완료: date={}, redis 활동 유저 {}명, 신규·갱신 {}건",
                date,
                userIds.size(),
                toSave.size());
        return toSave.size();
    }

    /** 그날 활동 유저 수 (적재 결과 기준) */
    public long countActiveUsers(LocalDate date) {
        return repository.countByActivityDate(date);
    }

    private Set<String> members(String key) {
        Set<String> members = redisTemplate.opsForSet().members(key);
        return members == null ? Set.of() : members;
    }
}
