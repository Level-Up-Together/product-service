package io.pinkspider.leveluptogethermvp.notificationservice.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.pinkspider.global.enums.NotificationType;
import io.pinkspider.global.facade.UserQueryFacade;
import io.pinkspider.leveluptogethermvp.notificationservice.domain.dto.NotificationResponse;
import io.pinkspider.leveluptogethermvp.notificationservice.domain.entity.AdminPushCampaign;
import io.pinkspider.leveluptogethermvp.notificationservice.domain.enums.AdminPushCampaignStatus;
import io.pinkspider.leveluptogethermvp.notificationservice.domain.enums.AdminPushTargetType;
import io.pinkspider.leveluptogethermvp.notificationservice.event.AdminPushCampaignCreatedEvent;
import io.pinkspider.leveluptogethermvp.notificationservice.infrastructure.AdminPushCampaignRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@DisplayName("AdminPushCampaignDispatcher 테스트 (LUT-508)")
class AdminPushCampaignDispatcherTest {

    @Mock private AdminPushCampaignRepository campaignRepository;
    @Mock private NotificationService notificationService;
    @Mock private UserQueryFacade userQueryFacade;

    private AdminPushCampaignDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        dispatcher = new AdminPushCampaignDispatcher(
            campaignRepository, notificationService, userQueryFacade, new ObjectMapper());
    }

    private AdminPushCampaign usersCampaign(String json, int count) {
        AdminPushCampaign c = AdminPushCampaign.create(
            "제목", "본문", "/shop", AdminPushTargetType.USERS, json, count, 42L);
        ReflectionTestUtils.setField(c, "id", 10L);
        return c;
    }

    @Test
    @DisplayName("유저별 알림 생성 결과를 sent/skipped/failed 로 세고 COMPLETED 로 마감한다")
    void dispatch_countsPerUserOutcome() {
        AdminPushCampaign campaign = usersCampaign("[\"u1\",\"u2\",\"u3\"]", 3);
        when(campaignRepository.findById(10L)).thenReturn(Optional.of(campaign));
        NotificationResponse created = NotificationResponse.builder().id(1L).build();
        when(notificationService.createNotification(eq("u1"), eq(NotificationType.ADMIN_PUSH),
                eq("제목"), eq("본문"), eq("ADMIN_PUSH"), eq(10L), eq("/shop"))).thenReturn(created);
        // u2: 시스템 알림 카테고리 off → 파이프라인이 null 반환 (스킵)
        when(notificationService.createNotification(eq("u2"), any(), anyString(), anyString(),
                anyString(), anyLong(), any())).thenReturn(null);
        // u3: 예외 (실패)
        when(notificationService.createNotification(eq("u3"), any(), anyString(), anyString(),
                anyString(), anyLong(), any())).thenThrow(new RuntimeException("db down"));

        dispatcher.onCampaignCreated(new AdminPushCampaignCreatedEvent(10L));

        assertThat(campaign.getStatus()).isEqualTo(AdminPushCampaignStatus.COMPLETED);
        assertThat(campaign.getSentCount()).isEqualTo(1);
        assertThat(campaign.getSkippedCount()).isEqualTo(1);
        assertThat(campaign.getFailedCount()).isEqualTo(1);
        assertThat(campaign.getStartedAt()).isNotNull();
        assertThat(campaign.getCompletedAt()).isNotNull();
        // SENDING 전이 1회 + 마감 1회 저장
        verify(campaignRepository, times(2)).save(campaign);
        verify(userQueryFacade, never()).findAllActiveUserIds();
    }

    @Test
    @DisplayName("전체 발송은 발송 시점의 활성 유저 전원을 다시 조회한다")
    void dispatch_all_resolvesActiveUsersAtSendTime() {
        AdminPushCampaign campaign = AdminPushCampaign.create(
            "제목", "본문", null, AdminPushTargetType.ALL, null, 2, 42L);
        ReflectionTestUtils.setField(campaign, "id", 11L);
        when(campaignRepository.findById(11L)).thenReturn(Optional.of(campaign));
        when(userQueryFacade.findAllActiveUserIds()).thenReturn(List.of("a", "b"));
        when(notificationService.createNotification(anyString(), eq(NotificationType.ADMIN_PUSH),
                anyString(), anyString(), anyString(), anyLong(), any()))
            .thenReturn(NotificationResponse.builder().id(1L).build());

        dispatcher.dispatch(11L);

        verify(notificationService, times(2)).createNotification(anyString(),
            eq(NotificationType.ADMIN_PUSH), eq("제목"), eq("본문"), eq("ADMIN_PUSH"), eq(11L), eq(null));
        assertThat(campaign.getSentCount()).isEqualTo(2);
        assertThat(campaign.getStatus()).isEqualTo(AdminPushCampaignStatus.COMPLETED);
    }

    @Test
    @DisplayName("PENDING 이 아닌 캠페인은 중복 트리거여도 다시 발송하지 않는다")
    void dispatch_nonPending_skipped() {
        AdminPushCampaign campaign = usersCampaign("[\"u1\"]", 1);
        campaign.complete(1, 0, 0, java.time.LocalDateTime.now());
        when(campaignRepository.findById(10L)).thenReturn(Optional.of(campaign));

        dispatcher.dispatch(10L);

        verify(notificationService, never()).createNotification(anyString(), any(), anyString(),
            anyString(), anyString(), anyLong(), any());
        verify(campaignRepository, never()).save(any());
    }

    @Test
    @DisplayName("대상 산출 자체가 실패하면 FAILED 로 마감하고 사유를 남긴다")
    void dispatch_resolveFailure_marksFailed() {
        AdminPushCampaign campaign = usersCampaign("not-json", 1);
        when(campaignRepository.findById(10L)).thenReturn(Optional.of(campaign));

        dispatcher.dispatch(10L);

        assertThat(campaign.getStatus()).isEqualTo(AdminPushCampaignStatus.FAILED);
        assertThat(campaign.getErrorMessage()).isNotBlank();
        assertThat(campaign.getCompletedAt()).isNotNull();
    }

    @Test
    @DisplayName("없는 캠페인 ID 는 예외 없이 스킵한다")
    void dispatch_missing_ignored() {
        when(campaignRepository.findById(404L)).thenReturn(Optional.empty());

        dispatcher.dispatch(404L);

        verify(campaignRepository, never()).save(any());
    }
}
