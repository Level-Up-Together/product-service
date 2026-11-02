package io.pinkspider.leveluptogethermvp.userservice.profile;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.pinkspider.global.event.UserLevelUpEvent;
import io.pinkspider.global.facade.dto.UserProfileInfo;
import io.pinkspider.leveluptogethermvp.missionservice.infrastructure.MissionCommentRepository;
import io.pinkspider.leveluptogethermvp.userservice.profile.application.UserProfileCacheService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * LUT-653: 레벨업 → 미션 댓글 레벨 스냅샷 동기화를 <b>실제 트랜잭션 단계</b>로 검증한다.
 *
 * <p>{@code UserLevelUpProfileSyncListener}(AFTER_COMMIT, 동기)는 커밋 후 콜백 안 — 트랜잭션 동기화가 해제된 뒤 — 에서
 * {@code UserProfileChangedEvent} 를 재발행한다. 수신 {@code MissionProfileSnapshotEventListener} 에 {@code
 * fallbackExecution} 이 없으면 "활성 트랜잭션 없음"으로 버려져 댓글 레벨이 갱신되지 않았다. 단위 테스트는 publisher 를 mock 해 이 유실을 못
 * 잡으므로 여기서 커밋을 실제로 일으킨다. 프로필·댓글 저장소는 mock — 검증 대상은 이벤트 전파이지 DB 가 아니다.
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("레벨업 → 미션 댓글 레벨 스냅샷 동기화 통합 테스트 (LUT-653)")
class UserLevelUpSnapshotSyncIntegrationTest {

    @Autowired private ApplicationEventPublisher eventPublisher;

    @Autowired
    @Qualifier("userTransactionManager")
    private PlatformTransactionManager userTransactionManager;

    @MockitoBean private UserProfileCacheService userProfileCacheService;

    @MockitoBean private MissionCommentRepository missionCommentRepository;

    @Test
    @DisplayName("트랜잭션 커밋 뒤 재발행된 프로필 변경 이벤트가 미션 댓글 레벨 갱신까지 닿는다")
    void levelUpCommitUpdatesMissionCommentLevel() {
        String userId = "lut653-user";
        // 레벨 7 — 10단위가 아니라 레벨업 피드 생성(FeedProjectionEventListener)은 건너뛴다
        when(userProfileCacheService.getUserProfile(userId))
                .thenReturn(new UserProfileInfo(userId, "닉네임", "pic.png", 7, null, null, null));

        new TransactionTemplate(userTransactionManager)
                .executeWithoutResult(
                        status -> {
                            eventPublisher.publishEvent(new UserLevelUpEvent(userId, 7, 1234L));
                            // 커밋 전에는 아무 리스너도 돌지 않는다
                            verify(missionCommentRepository, never())
                                    .updateUserProfileByUserId(
                                            anyString(), anyString(), anyString(), anyInt());
                        });

        // 수신 리스너는 @Async(eventExecutor) 라 잠시 기다린다
        verify(missionCommentRepository, timeout(5_000))
                .updateUserProfileByUserId(userId, "닉네임", "pic.png", 7);
        verify(userProfileCacheService).evictUserProfileCache(userId);
    }
}
