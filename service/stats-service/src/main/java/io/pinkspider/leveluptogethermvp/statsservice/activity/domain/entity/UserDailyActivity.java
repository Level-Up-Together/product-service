package io.pinkspider.leveluptogethermvp.statsservice.activity.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collection;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Comment;

/**
 * LUT-598: 유저 × KST 날짜 1행. 그날 인증된 API 요청을 1건 이상 보냈다는 뜻 — DAU/WAU/MAU·리텐션의 원천.
 *
 * <p>Redis {@code active:{날짜}} 집합을 매일 00:20 KST 에 flush 해 만든다. (user_id, activity_date) 유니크라 재실행이
 * 멱등이다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "user_daily_activity",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uk_user_daily_activity",
                        columnNames = {"user_id", "activity_date"}),
        indexes = @Index(name = "idx_user_daily_activity_date", columnList = "activity_date"))
@Comment("유저 일별 활동 (LUT-598)")
public class UserDailyActivity {

    public static final int DEVICE_TYPES_MAX_LENGTH = 50;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "user_id", nullable = false, length = 36)
    @Comment("사용자 ID")
    private String userId;

    @Column(name = "activity_date", nullable = false)
    @Comment("활동 날짜 (KST)")
    private LocalDate activityDate;

    @Column(name = "device_types", length = DEVICE_TYPES_MAX_LENGTH)
    @Comment("그날 사용한 기기 종류, 정렬·쉼표 구분 (예: ios,web)")
    private String deviceTypes;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Comment("생성 일시")
    private LocalDateTime createdAt;

    public static UserDailyActivity create(
            String userId, LocalDate activityDate, Collection<String> deviceTypes) {
        UserDailyActivity row = new UserDailyActivity();
        row.userId = userId;
        row.activityDate = activityDate;
        row.deviceTypes = join(deviceTypes);
        row.createdAt = LocalDateTime.now();
        return row;
    }

    /** 기기 종류를 합친다. 저장값이 바뀌면 true */
    public boolean mergeDeviceTypes(Collection<String> incoming) {
        Set<String> merged = deviceTypeSet();
        merged.addAll(incoming);
        String joined = join(merged);
        if (Objects.equals(joined, deviceTypes)) {
            return false;
        }
        deviceTypes = joined;
        return true;
    }

    /** 저장된 기기 종류 (정렬된 가변 집합) */
    public Set<String> deviceTypeSet() {
        Set<String> result = new TreeSet<>();
        if (deviceTypes == null || deviceTypes.isBlank()) {
            return result;
        }
        Arrays.stream(deviceTypes.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .forEach(result::add);
        return result;
    }

    /** 정렬·중복 제거·쉼표 결합. 비면 null, 컬럼 길이를 넘으면 자른다 */
    static String join(Collection<String> types) {
        if (types == null) {
            return null;
        }
        String joined =
                types.stream()
                        .filter(t -> t != null && !t.isBlank())
                        .map(String::trim)
                        .distinct()
                        .sorted()
                        .collect(Collectors.joining(","));
        if (joined.isEmpty()) {
            return null;
        }
        return joined.length() > DEVICE_TYPES_MAX_LENGTH
                ? joined.substring(0, DEVICE_TYPES_MAX_LENGTH)
                : joined;
    }
}
