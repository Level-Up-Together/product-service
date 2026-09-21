package io.pinkspider.leveluptogethermvp.notificationservice.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.pinkspider.global.exception.CustomException;
import io.pinkspider.global.facade.UserQueryFacade;
import io.pinkspider.leveluptogethermvp.notificationservice.domain.dto.AdminPushCampaignRequest;
import io.pinkspider.leveluptogethermvp.notificationservice.domain.dto.AdminPushCampaignResponse;
import io.pinkspider.leveluptogethermvp.notificationservice.domain.entity.AdminPushCampaign;
import io.pinkspider.leveluptogethermvp.notificationservice.domain.enums.AdminPushCampaignStatus;
import io.pinkspider.leveluptogethermvp.notificationservice.domain.enums.AdminPushTargetType;
import io.pinkspider.leveluptogethermvp.notificationservice.event.AdminPushCampaignCreatedEvent;
import io.pinkspider.leveluptogethermvp.notificationservice.infrastructure.AdminPushCampaignRepository;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
@DisplayName("AdminPushCampaignService 테스트 (LUT-508)")
class AdminPushCampaignServiceTest {

    @Mock private AdminPushCampaignRepository campaignRepository;
    @Mock private UserQueryFacade userQueryFacade;
    @Mock private ApplicationEventPublisher eventPublisher;

    private AdminPushCampaignService service;

    @BeforeEach
    void setUp() {
        service = new AdminPushCampaignService(
            campaignRepository, userQueryFacade, eventPublisher, new ObjectMapper());
    }

    private AdminPushCampaignRequest request(AdminPushTargetType type, List<String> userIds) {
        return AdminPushCampaignRequest.builder()
            .title("  이벤트 안내  ")
            .body("오늘만 다이아 2배!")
            .actionUrl(" ")
            .targetType(type)
            .userIds(userIds)
            .build();
    }

    @Test
    @DisplayName("전체 발송: 활성 유저 전원을 대상으로 이력을 만들고 커밋 후 발송 이벤트를 발행한다")
    void create_all_targetsActiveUsers() {
        when(userQueryFacade.findAllActiveUserIds()).thenReturn(List.of("u1", "u2", "u3"));
        when(campaignRepository.save(any(AdminPushCampaign.class))).thenAnswer(inv -> {
            AdminPushCampaign c = inv.getArgument(0);
            org.springframework.test.util.ReflectionTestUtils.setField(c, "id", 7L);
            return c;
        });

        AdminPushCampaignResponse response = service.create(request(AdminPushTargetType.ALL, null), 42L);

        ArgumentCaptor<AdminPushCampaign> captor = ArgumentCaptor.forClass(AdminPushCampaign.class);
        verify(campaignRepository).save(captor.capture());
        AdminPushCampaign saved = captor.getValue();
        assertThat(saved.getTitle()).isEqualTo("이벤트 안내"); // trim
        assertThat(saved.getActionUrl()).isNull(); // blank → null
        assertThat(saved.getTargetType()).isEqualTo(AdminPushTargetType.ALL);
        assertThat(saved.getTargetUserIds()).isNull(); // ALL 은 목록을 저장하지 않는다
        assertThat(saved.getTargetCount()).isEqualTo(3);
        assertThat(saved.getStatus()).isEqualTo(AdminPushCampaignStatus.PENDING);
        assertThat(saved.getRequestedBy()).isEqualTo(42L);
        assertThat(response.id()).isEqualTo(7L);
        assertThat(response.targetUserIds()).isNull();
        verify(eventPublisher).publishEvent(new AdminPushCampaignCreatedEvent(7L));
    }

    @Test
    @DisplayName("일부 발송: 중복·공백 제거 후 활성 유저만 남기고 JSON 으로 저장한다")
    void create_users_filtersActiveAndStoresJson() {
        when(userQueryFacade.getActiveUserIds(List.of("u1", "u2", "u9")))
            .thenReturn(List.of("u1", "u2"));
        when(campaignRepository.save(any(AdminPushCampaign.class))).thenAnswer(inv -> inv.getArgument(0));

        AdminPushCampaignResponse response = service.create(
            request(AdminPushTargetType.USERS, Arrays.asList(" u1 ", "u2", "u1", "", null, "u9")), 42L);

        ArgumentCaptor<AdminPushCampaign> captor = ArgumentCaptor.forClass(AdminPushCampaign.class);
        verify(campaignRepository).save(captor.capture());
        assertThat(captor.getValue().getTargetUserIds()).isEqualTo("[\"u1\",\"u2\"]");
        assertThat(captor.getValue().getTargetCount()).isEqualTo(2);
        assertThat(response.targetUserIds()).containsExactly("u1", "u2");
    }

    @Test
    @DisplayName("일부 발송에 유저 ID 가 없으면 140101 — 이력을 만들지 않는다")
    void create_users_empty_throws() {
        assertThatThrownBy(() -> service.create(request(AdminPushTargetType.USERS, List.of(" ")), 42L))
            .isInstanceOf(CustomException.class)
            .hasFieldOrPropertyWithValue("code", "140101");
        verify(campaignRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("요청한 유저가 모두 비활성/미존재면 140102")
    void create_users_noneActive_throws() {
        when(userQueryFacade.getActiveUserIds(List.of("ghost"))).thenReturn(List.of());

        assertThatThrownBy(() -> service.create(request(AdminPushTargetType.USERS, List.of("ghost")), 42L))
            .isInstanceOf(CustomException.class)
            .hasFieldOrPropertyWithValue("code", "140102");
    }

    @Test
    @DisplayName("전체 발송인데 활성 유저가 없으면 140102")
    void create_all_noneActive_throws() {
        when(userQueryFacade.findAllActiveUserIds()).thenReturn(List.of());

        assertThatThrownBy(() -> service.create(request(AdminPushTargetType.ALL, null), 42L))
            .isInstanceOf(CustomException.class)
            .hasFieldOrPropertyWithValue("code", "140102");
    }

    @Test
    @DisplayName("상세 조회는 저장된 대상 유저 JSON 을 목록으로 풀어 준다")
    void getCampaign_parsesTargetUserIds() {
        AdminPushCampaign campaign = AdminPushCampaign.create(
            "t", "b", "/shop", AdminPushTargetType.USERS, "[\"u1\",\"u2\"]", 2, 1L);
        when(campaignRepository.findById(5L)).thenReturn(Optional.of(campaign));

        AdminPushCampaignResponse response = service.getCampaign(5L);

        assertThat(response.targetUserIds()).containsExactly("u1", "u2");
        assertThat(response.actionUrl()).isEqualTo("/shop");
    }

    @Test
    @DisplayName("없는 캠페인 상세는 140103")
    void getCampaign_notFound() {
        when(campaignRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getCampaign(99L))
            .isInstanceOf(CustomException.class)
            .hasFieldOrPropertyWithValue("code", "140103");
    }
}
