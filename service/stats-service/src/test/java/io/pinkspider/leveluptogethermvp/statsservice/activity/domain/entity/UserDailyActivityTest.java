package io.pinkspider.leveluptogethermvp.statsservice.activity.domain.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("UserDailyActivity 엔티티 테스트 (LUT-598)")
class UserDailyActivityTest {

    private static final LocalDate DATE = LocalDate.of(2026, 10, 23);

    @Nested
    @DisplayName("create")
    class Create {

        @Test
        @DisplayName("기기 종류를 정렬·중복 제거해 쉼표로 합치고 생성 시각을 채운다")
        void sortsAndDedups() {
            UserDailyActivity row =
                    UserDailyActivity.create("u1", DATE, List.of("web", "ios", "web", " android "));

            assertThat(row.getUserId()).isEqualTo("u1");
            assertThat(row.getActivityDate()).isEqualTo(DATE);
            assertThat(row.getDeviceTypes()).isEqualTo("android,ios,web");
            assertThat(row.getCreatedAt()).isNotNull();
            assertThat(row.deviceTypeSet()).containsExactly("android", "ios", "web");
        }

        @Test
        @DisplayName("기기 종류가 없거나 빈 값만 있으면 null 로 저장한다")
        void emptyBecomesNull() {
            assertThat(UserDailyActivity.create("u1", DATE, Set.of()).getDeviceTypes()).isNull();
            assertThat(UserDailyActivity.create("u1", DATE, List.of("", " ")).getDeviceTypes())
                    .isNull();
            assertThat(UserDailyActivity.create("u1", DATE, null).getDeviceTypes()).isNull();
            assertThat(UserDailyActivity.create("u1", DATE, null).deviceTypeSet()).isEmpty();
        }

        @Test
        @DisplayName("컬럼 길이(50)를 넘으면 잘라서 저장한다")
        void truncates() {
            List<String> many =
                    List.of("aaaaaaaaaa", "bbbbbbbbbb", "cccccccccc", "dddddddddd", "eeeeeeeeee");
            UserDailyActivity row = UserDailyActivity.create("u1", DATE, many);
            assertThat(row.getDeviceTypes()).hasSize(UserDailyActivity.DEVICE_TYPES_MAX_LENGTH);
        }
    }

    @Nested
    @DisplayName("mergeDeviceTypes")
    class Merge {

        @Test
        @DisplayName("새 기기가 추가되면 합쳐 저장하고 true")
        void addsNewType() {
            UserDailyActivity row = UserDailyActivity.create("u1", DATE, List.of("web"));

            assertThat(row.mergeDeviceTypes(List.of("ios"))).isTrue();
            assertThat(row.getDeviceTypes()).isEqualTo("ios,web");
        }

        @Test
        @DisplayName("이미 있는 기기만 들어오면 바뀌지 않고 false")
        void unchanged() {
            UserDailyActivity row = UserDailyActivity.create("u1", DATE, List.of("ios", "web"));

            assertThat(row.mergeDeviceTypes(List.of("web"))).isFalse();
            assertThat(row.mergeDeviceTypes(Set.of())).isFalse();
            assertThat(row.getDeviceTypes()).isEqualTo("ios,web");
        }

        @Test
        @DisplayName("저장값이 null 이고 들어온 것도 없으면 false, 있으면 채우고 true")
        void fromNull() {
            UserDailyActivity row = UserDailyActivity.create("u1", DATE, null);

            assertThat(row.mergeDeviceTypes(Set.of())).isFalse();
            assertThat(row.getDeviceTypes()).isNull();
            assertThat(row.mergeDeviceTypes(List.of("android"))).isTrue();
            assertThat(row.getDeviceTypes()).isEqualTo("android");
        }
    }
}
