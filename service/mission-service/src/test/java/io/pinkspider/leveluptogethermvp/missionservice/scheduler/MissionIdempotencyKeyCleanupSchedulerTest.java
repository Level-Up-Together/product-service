package io.pinkspider.leveluptogethermvp.missionservice.scheduler;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import io.pinkspider.leveluptogethermvp.missionservice.application.MissionIdempotencyService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("MissionIdempotencyKeyCleanupScheduler (LUT-577)")
class MissionIdempotencyKeyCleanupSchedulerTest {

    @Mock private MissionIdempotencyService missionIdempotencyService;
    @InjectMocks private MissionIdempotencyKeyCleanupScheduler scheduler;

    @Test
    @DisplayName("만료 키 정리를 위임한다")
    void cleanup_delegates() {
        scheduler.cleanupExpiredKeys();

        verify(missionIdempotencyService).cleanupExpired();
    }

    @Test
    @DisplayName("정리 중 예외는 삼키고 로그만 남긴다 (스케줄러 중단 방지)")
    void cleanup_swallowsException() {
        doThrow(new RuntimeException("db down")).when(missionIdempotencyService).cleanupExpired();

        scheduler.cleanupExpiredKeys();

        verify(missionIdempotencyService).cleanupExpired();
    }
}
