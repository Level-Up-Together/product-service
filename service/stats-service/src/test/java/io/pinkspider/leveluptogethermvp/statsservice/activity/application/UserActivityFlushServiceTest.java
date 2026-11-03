package io.pinkspider.leveluptogethermvp.statsservice.activity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.pinkspider.leveluptogethermvp.statsservice.activity.domain.entity.UserDailyActivity;
import io.pinkspider.leveluptogethermvp.statsservice.activity.infrastructure.UserDailyActivityRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserActivityFlushService 테스트 (LUT-598)")
class UserActivityFlushServiceTest {

    private static final LocalDate DATE = LocalDate.of(2026, 10, 22);

    @Mock private StringRedisTemplate redisTemplate;
    @Mock private SetOperations<String, String> setOperations;
    @Mock private UserDailyActivityRepository repository;

    @InjectMocks private UserActivityFlushService service;

    @BeforeEach
    void setUp() {
        // countActiveUsers 는 Redis 를 쓰지 않으므로 lenient
        lenient().when(redisTemplate.opsForSet()).thenReturn(setOperations);
    }

    @Test
    @DisplayName("일별 집합이 비어 있으면 0 을 돌려주고 DB 를 건드리지 않는다")
    void emptySet() {
        when(setOperations.members("active:2026-10-22")).thenReturn(Set.of());

        assertThat(service.flush(DATE)).isZero();

        verify(repository, never()).findAllByActivityDate(any());
        verify(repository, never()).saveAll(anyList());
    }

    @Test
    @DisplayName("members 가 null(키 없음)이어도 0")
    void nullSet() {
        when(setOperations.members("active:2026-10-22")).thenReturn(null);

        assertThat(service.flush(DATE)).isZero();
    }

    @Test
    @DisplayName("새 유저는 기기 종류를 합쳐 행을 만들고, 기기 집합에 없는 유저는 기기 null 로 만든다")
    void insertsNewRows() {
        when(setOperations.members("active:2026-10-22")).thenReturn(Set.of("u1", "u2"));
        when(setOperations.members("active:2026-10-22:web")).thenReturn(Set.of("u1"));
        when(setOperations.members("active:2026-10-22:ios")).thenReturn(Set.of("u1"));
        when(setOperations.members("active:2026-10-22:ipad")).thenReturn(null);
        when(setOperations.members("active:2026-10-22:android")).thenReturn(Set.of());
        when(repository.findAllByActivityDate(DATE)).thenReturn(List.of());

        int flushed = service.flush(DATE);

        assertThat(flushed).isEqualTo(2);
        ArgumentCaptor<List<UserDailyActivity>> captor = ArgumentCaptor.forClass(List.class);
        verify(repository).saveAll(captor.capture());
        List<UserDailyActivity> saved = captor.getValue();
        assertThat(saved).hasSize(2);
        UserDailyActivity u1 =
                saved.stream().filter(r -> r.getUserId().equals("u1")).findFirst().orElseThrow();
        UserDailyActivity u2 =
                saved.stream().filter(r -> r.getUserId().equals("u2")).findFirst().orElseThrow();
        assertThat(u1.getDeviceTypes()).isEqualTo("ios,web");
        assertThat(u1.getActivityDate()).isEqualTo(DATE);
        assertThat(u2.getDeviceTypes()).isNull();
    }

    @Test
    @DisplayName("이미 있는 행은 기기 종류가 늘어난 것만 갱신한다 (재실행 멱등)")
    void mergesExistingRows() {
        when(setOperations.members(anyString())).thenReturn(Set.of());
        when(setOperations.members("active:2026-10-22")).thenReturn(Set.of("u1", "u2"));
        when(setOperations.members("active:2026-10-22:android")).thenReturn(Set.of("u2"));
        UserDailyActivity existingU1 = UserDailyActivity.create("u1", DATE, List.of("web"));
        UserDailyActivity existingU2 = UserDailyActivity.create("u2", DATE, List.of("web"));
        when(repository.findAllByActivityDate(DATE)).thenReturn(List.of(existingU1, existingU2));

        int flushed = service.flush(DATE);

        assertThat(flushed).isEqualTo(1);
        ArgumentCaptor<List<UserDailyActivity>> captor = ArgumentCaptor.forClass(List.class);
        verify(repository).saveAll(captor.capture());
        assertThat(captor.getValue()).containsExactly(existingU2);
        assertThat(existingU2.getDeviceTypes()).isEqualTo("android,web");
        assertThat(existingU1.getDeviceTypes()).isEqualTo("web");
    }

    @Test
    @DisplayName("countActiveUsers 는 적재된 행 수를 돌려준다")
    void countActiveUsers() {
        when(repository.countByActivityDate(DATE)).thenReturn(17L);

        assertThat(service.countActiveUsers(DATE)).isEqualTo(17L);
    }
}
