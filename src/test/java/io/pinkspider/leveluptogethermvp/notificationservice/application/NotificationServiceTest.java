package io.pinkspider.leveluptogethermvp.notificationservice.application;

import static io.pinkspider.global.test.TestReflectionUtils.setId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.pinkspider.leveluptogethermvp.notificationservice.domain.dto.NotificationPreferenceRequest;
import io.pinkspider.leveluptogethermvp.notificationservice.domain.dto.NotificationPreferenceResponse;
import io.pinkspider.leveluptogethermvp.notificationservice.domain.dto.NotificationResponse;
import io.pinkspider.leveluptogethermvp.notificationservice.domain.dto.NotificationSummaryResponse;
import io.pinkspider.leveluptogethermvp.notificationservice.domain.entity.Notification;
import io.pinkspider.leveluptogethermvp.notificationservice.domain.entity.NotificationPreference;
import io.pinkspider.leveluptogethermvp.notificationservice.domain.enums.NotificationType;
import io.pinkspider.leveluptogethermvp.notificationservice.infrastructure.NotificationPreferenceRepository;
import io.pinkspider.leveluptogethermvp.notificationservice.infrastructure.NotificationRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private NotificationPreferenceRepository preferenceRepository;

    @InjectMocks
    private NotificationService notificationService;

    private static final String TEST_USER_ID = "test-user-123";

    private Notification createTestNotification(Long id, String userId, NotificationType type) {
        Notification notification = Notification.builder()
            .userId(userId)
            .notificationType(type)
            .title("테스트 알림")
            .message("테스트 메시지")
            .isRead(false)
            .isPushed(false)
            .build();
        setId(notification, id);
        return notification;
    }

    private NotificationPreference createTestPreference(Long id, String userId) {
        NotificationPreference preference = NotificationPreference.builder()
            .userId(userId)
            .pushEnabled(true)
            .friendNotifications(true)
            .guildNotifications(true)
            .socialNotifications(true)
            .systemNotifications(true)
            .quietHoursEnabled(false)
            .build();
        setId(preference, id);
        return preference;
    }

    @Nested
    @DisplayName("createNotification 테스트")
    class CreateNotificationTest {

        @Test
        @DisplayName("알림을 정상적으로 생성한다")
        void createNotification_success() {
            // given
            NotificationPreference preference = createTestPreference(1L, TEST_USER_ID);
            Notification savedNotification = createTestNotification(1L, TEST_USER_ID, NotificationType.SYSTEM);

            when(preferenceRepository.findByUserId(TEST_USER_ID)).thenReturn(Optional.of(preference));
            when(notificationRepository.save(any(Notification.class))).thenReturn(savedNotification);

            // when
            NotificationResponse result = notificationService.createNotification(
                TEST_USER_ID, NotificationType.SYSTEM, "테스트", "메시지");

            // then
            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(1L);
            verify(notificationRepository).save(any(Notification.class));
        }

        @Test
        @DisplayName("알림 설정이 비활성화되어 있으면 알림을 생성하지 않는다")
        void createNotification_categoryDisabled_returnsNull() {
            // given
            NotificationPreference preference = NotificationPreference.builder()
                .userId(TEST_USER_ID)
                .friendNotifications(false)  // 친구 알림 비활성화
                .build();
            setId(preference, 1L);

            when(preferenceRepository.findByUserId(TEST_USER_ID)).thenReturn(Optional.of(preference));

            // when
            NotificationResponse result = notificationService.createNotification(
                TEST_USER_ID, NotificationType.FRIEND_REQUEST, "친구 요청", "메시지");

            // then
            assertThat(result).isNull();
            verify(notificationRepository, never()).save(any());
        }

        @Test
        @DisplayName("알림 설정이 없으면 기본 설정을 생성하고 알림을 생성한다")
        void createNotification_noPreference_createsDefault() {
            // given
            NotificationPreference newPreference = NotificationPreference.createDefault(TEST_USER_ID);
            setId(newPreference, 1L);
            Notification savedNotification = createTestNotification(1L, TEST_USER_ID, NotificationType.SYSTEM);

            when(preferenceRepository.findByUserId(TEST_USER_ID)).thenReturn(Optional.empty());
            when(preferenceRepository.save(any(NotificationPreference.class))).thenReturn(newPreference);
            when(notificationRepository.save(any(Notification.class))).thenReturn(savedNotification);

            // when
            NotificationResponse result = notificationService.createNotification(
                TEST_USER_ID, NotificationType.SYSTEM, "테스트", "메시지");

            // then
            assertThat(result).isNotNull();
            verify(preferenceRepository).save(any(NotificationPreference.class));
        }

        @Test
        @DisplayName("참조 정보를 포함하여 알림을 생성한다")
        void createNotification_withReference_success() {
            // given
            NotificationPreference preference = createTestPreference(1L, TEST_USER_ID);
            Notification savedNotification = Notification.builder()
                .userId(TEST_USER_ID)
                .notificationType(NotificationType.GUILD_INVITE)
                .title("길드 초대")
                .message("테스트 길드에 초대되었습니다")
                .referenceType("GUILD")
                .referenceId(100L)
                .actionUrl("/guild/100")
                .build();
            setId(savedNotification, 1L);

            when(preferenceRepository.findByUserId(TEST_USER_ID)).thenReturn(Optional.of(preference));
            when(notificationRepository.save(any(Notification.class))).thenReturn(savedNotification);

            // when
            NotificationResponse result = notificationService.createNotification(
                TEST_USER_ID, NotificationType.GUILD_INVITE, "길드 초대",
                "테스트 길드에 초대되었습니다", "GUILD", 100L, "/guild/100");

            // then
            assertThat(result).isNotNull();
            assertThat(result.getReferenceType()).isEqualTo("GUILD");
            assertThat(result.getReferenceId()).isEqualTo(100L);
        }
    }

    @Nested
    @DisplayName("getNotifications 테스트")
    class GetNotificationsTest {

        @Test
        @DisplayName("알림 목록을 페이지로 조회한다")
        void getNotifications_success() {
            // given
            Pageable pageable = PageRequest.of(0, 10);
            List<Notification> notifications = List.of(
                createTestNotification(1L, TEST_USER_ID, NotificationType.SYSTEM),
                createTestNotification(2L, TEST_USER_ID, NotificationType.FRIEND_REQUEST)
            );
            Page<Notification> page = new PageImpl<>(notifications, pageable, 2);

            when(notificationRepository.findByUserIdOrderByCreatedAtDesc(TEST_USER_ID, pageable))
                .thenReturn(page);

            // when
            Page<NotificationResponse> result = notificationService.getNotifications(TEST_USER_ID, pageable);

            // then
            assertThat(result.getContent()).hasSize(2);
            assertThat(result.getTotalElements()).isEqualTo(2);
        }

        @Test
        @DisplayName("알림이 없으면 빈 페이지를 반환한다")
        void getNotifications_empty() {
            // given
            Pageable pageable = PageRequest.of(0, 10);
            Page<Notification> emptyPage = new PageImpl<>(List.of(), pageable, 0);

            when(notificationRepository.findByUserIdOrderByCreatedAtDesc(TEST_USER_ID, pageable))
                .thenReturn(emptyPage);

            // when
            Page<NotificationResponse> result = notificationService.getNotifications(TEST_USER_ID, pageable);

            // then
            assertThat(result.getContent()).isEmpty();
            assertThat(result.getTotalElements()).isZero();
        }
    }

    @Nested
    @DisplayName("getUnreadNotifications 테스트")
    class GetUnreadNotificationsTest {

        @Test
        @DisplayName("읽지 않은 알림 목록을 조회한다")
        void getUnreadNotifications_success() {
            // given
            List<Notification> unreadNotifications = List.of(
                createTestNotification(1L, TEST_USER_ID, NotificationType.SYSTEM),
                createTestNotification(2L, TEST_USER_ID, NotificationType.FRIEND_REQUEST)
            );

            when(notificationRepository.findUnreadByUserId(TEST_USER_ID)).thenReturn(unreadNotifications);

            // when
            List<NotificationResponse> result = notificationService.getUnreadNotifications(TEST_USER_ID);

            // then
            assertThat(result).hasSize(2);
        }
    }

    @Nested
    @DisplayName("getNotificationSummary 테스트")
    class GetNotificationSummaryTest {

        @Test
        @DisplayName("읽지 않은 알림 수를 조회한다")
        void getNotificationSummary_success() {
            // given
            when(notificationRepository.countUnreadByUserId(TEST_USER_ID)).thenReturn(5);

            // when
            NotificationSummaryResponse result = notificationService.getNotificationSummary(TEST_USER_ID);

            // then
            assertThat(result.getUnreadCount()).isEqualTo(5);
        }

        @Test
        @DisplayName("읽지 않은 알림이 없으면 0을 반환한다")
        void getNotificationSummary_noUnread() {
            // given
            when(notificationRepository.countUnreadByUserId(TEST_USER_ID)).thenReturn(0);

            // when
            NotificationSummaryResponse result = notificationService.getNotificationSummary(TEST_USER_ID);

            // then
            assertThat(result.getUnreadCount()).isZero();
        }
    }

    @Nested
    @DisplayName("markAsRead 테스트")
    class MarkAsReadTest {

        @Test
        @DisplayName("알림을 읽음 처리한다")
        void markAsRead_success() {
            // given
            Long notificationId = 1L;
            Notification notification = createTestNotification(notificationId, TEST_USER_ID, NotificationType.SYSTEM);

            when(notificationRepository.findById(notificationId)).thenReturn(Optional.of(notification));

            // when
            NotificationResponse result = notificationService.markAsRead(TEST_USER_ID, notificationId);

            // then
            assertThat(result).isNotNull();
            assertThat(notification.getIsRead()).isTrue();
        }

        @Test
        @DisplayName("존재하지 않는 알림을 읽음 처리하면 예외가 발생한다")
        void markAsRead_notFound_throwsException() {
            // given
            Long notificationId = 999L;

            when(notificationRepository.findById(notificationId)).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> notificationService.markAsRead(TEST_USER_ID, notificationId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("알림을 찾을 수 없습니다.");
        }

        @Test
        @DisplayName("다른 사용자의 알림을 읽음 처리하면 예외가 발생한다")
        void markAsRead_otherUser_throwsException() {
            // given
            Long notificationId = 1L;
            String otherUserId = "other-user-456";
            Notification notification = createTestNotification(notificationId, otherUserId, NotificationType.SYSTEM);

            when(notificationRepository.findById(notificationId)).thenReturn(Optional.of(notification));

            // when & then
            assertThatThrownBy(() -> notificationService.markAsRead(TEST_USER_ID, notificationId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("본인의 알림만 읽음 처리할 수 있습니다.");
        }
    }

    @Nested
    @DisplayName("markAllAsRead 테스트")
    class MarkAllAsReadTest {

        @Test
        @DisplayName("모든 알림을 읽음 처리한다")
        void markAllAsRead_success() {
            // given
            when(notificationRepository.markAllAsRead(TEST_USER_ID)).thenReturn(5);

            // when
            int result = notificationService.markAllAsRead(TEST_USER_ID);

            // then
            assertThat(result).isEqualTo(5);
            verify(notificationRepository).markAllAsRead(TEST_USER_ID);
        }
    }

    @Nested
    @DisplayName("deleteNotification 테스트")
    class DeleteNotificationTest {

        @Test
        @DisplayName("알림을 삭제한다")
        void deleteNotification_success() {
            // given
            Long notificationId = 1L;
            Notification notification = createTestNotification(notificationId, TEST_USER_ID, NotificationType.SYSTEM);

            when(notificationRepository.findById(notificationId)).thenReturn(Optional.of(notification));

            // when
            notificationService.deleteNotification(TEST_USER_ID, notificationId);

            // then
            verify(notificationRepository).delete(notification);
        }

        @Test
        @DisplayName("존재하지 않는 알림을 삭제하면 예외가 발생한다")
        void deleteNotification_notFound_throwsException() {
            // given
            Long notificationId = 999L;

            when(notificationRepository.findById(notificationId)).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> notificationService.deleteNotification(TEST_USER_ID, notificationId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("알림을 찾을 수 없습니다.");
        }

        @Test
        @DisplayName("다른 사용자의 알림을 삭제하면 예외가 발생한다")
        void deleteNotification_otherUser_throwsException() {
            // given
            Long notificationId = 1L;
            String otherUserId = "other-user-456";
            Notification notification = createTestNotification(notificationId, otherUserId, NotificationType.SYSTEM);

            when(notificationRepository.findById(notificationId)).thenReturn(Optional.of(notification));

            // when & then
            assertThatThrownBy(() -> notificationService.deleteNotification(TEST_USER_ID, notificationId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("본인의 알림만 삭제할 수 있습니다.");
        }
    }

    @Nested
    @DisplayName("deleteByReference 테스트")
    class DeleteByReferenceTest {

        @Test
        @DisplayName("참조 정보로 알림을 삭제한다")
        void deleteByReference_success() {
            // given
            when(notificationRepository.deleteByReference("FRIEND_REQUEST", 100L)).thenReturn(2);

            // when
            int result = notificationService.deleteByReference("FRIEND_REQUEST", 100L);

            // then
            assertThat(result).isEqualTo(2);
        }

        @Test
        @DisplayName("삭제할 알림이 없으면 0을 반환한다")
        void deleteByReference_noMatch() {
            // given
            when(notificationRepository.deleteByReference("FRIEND_REQUEST", 999L)).thenReturn(0);

            // when
            int result = notificationService.deleteByReference("FRIEND_REQUEST", 999L);

            // then
            assertThat(result).isZero();
        }
    }

    @Nested
    @DisplayName("getPreferences 테스트")
    class GetPreferencesTest {

        @Test
        @DisplayName("알림 설정을 조회한다")
        void getPreferences_success() {
            // given
            NotificationPreference preference = createTestPreference(1L, TEST_USER_ID);

            when(preferenceRepository.findByUserId(TEST_USER_ID)).thenReturn(Optional.of(preference));

            // when
            NotificationPreferenceResponse result = notificationService.getPreferences(TEST_USER_ID);

            // then
            assertThat(result).isNotNull();
            assertThat(result.getPushEnabled()).isTrue();
        }

        @Test
        @DisplayName("알림 설정이 없으면 기본 설정을 생성하여 반환한다")
        void getPreferences_createsDefault() {
            // given
            NotificationPreference newPreference = NotificationPreference.createDefault(TEST_USER_ID);
            setId(newPreference, 1L);

            when(preferenceRepository.findByUserId(TEST_USER_ID)).thenReturn(Optional.empty());
            when(preferenceRepository.save(any(NotificationPreference.class))).thenReturn(newPreference);

            // when
            NotificationPreferenceResponse result = notificationService.getPreferences(TEST_USER_ID);

            // then
            assertThat(result).isNotNull();
            verify(preferenceRepository).save(any(NotificationPreference.class));
        }
    }

    @Nested
    @DisplayName("updatePreferences 테스트")
    class UpdatePreferencesTest {

        @Test
        @DisplayName("알림 설정을 업데이트한다")
        void updatePreferences_success() {
            // given
            NotificationPreference preference = createTestPreference(1L, TEST_USER_ID);
            NotificationPreferenceRequest request = NotificationPreferenceRequest.builder()
                .pushEnabled(false)
                .friendNotifications(false)
                .quietHoursEnabled(true)
                .quietHoursStart("22:00")
                .quietHoursEnd("08:00")
                .build();

            when(preferenceRepository.findByUserId(TEST_USER_ID)).thenReturn(Optional.of(preference));

            // when
            NotificationPreferenceResponse result = notificationService.updatePreferences(TEST_USER_ID, request);

            // then
            assertThat(result).isNotNull();
            assertThat(preference.getPushEnabled()).isFalse();
            assertThat(preference.getFriendNotifications()).isFalse();
            assertThat(preference.getQuietHoursEnabled()).isTrue();
        }

        @Test
        @DisplayName("일부 필드만 업데이트한다")
        void updatePreferences_partialUpdate() {
            // given
            NotificationPreference preference = createTestPreference(1L, TEST_USER_ID);
            NotificationPreferenceRequest request = NotificationPreferenceRequest.builder()
                .pushEnabled(false)  // 이것만 업데이트
                .build();

            when(preferenceRepository.findByUserId(TEST_USER_ID)).thenReturn(Optional.of(preference));

            // when
            NotificationPreferenceResponse result = notificationService.updatePreferences(TEST_USER_ID, request);

            // then
            assertThat(preference.getPushEnabled()).isFalse();
            assertThat(preference.getFriendNotifications()).isTrue();  // 기존 값 유지
            assertThat(preference.getGuildNotifications()).isTrue();  // 기존 값 유지
        }
    }

    @Nested
    @DisplayName("cleanupExpiredNotifications 테스트")
    class CleanupExpiredNotificationsTest {

        @Test
        @DisplayName("만료된 알림을 삭제한다")
        void cleanupExpiredNotifications_success() {
            // given
            when(notificationRepository.deleteExpiredNotifications(any(LocalDateTime.class))).thenReturn(10);

            // when
            int result = notificationService.cleanupExpiredNotifications();

            // then
            assertThat(result).isEqualTo(10);
        }
    }

    @Nested
    @DisplayName("편의 메서드 테스트")
    class ConvenienceMethodsTest {

        @Test
        @DisplayName("친구 요청 알림을 생성한다")
        void notifyFriendRequest_success() {
            // given
            NotificationPreference preference = createTestPreference(1L, TEST_USER_ID);
            Notification savedNotification = createTestNotification(1L, TEST_USER_ID, NotificationType.FRIEND_REQUEST);

            when(preferenceRepository.findByUserId(TEST_USER_ID)).thenReturn(Optional.of(preference));
            when(notificationRepository.save(any(Notification.class))).thenReturn(savedNotification);

            // when
            notificationService.notifyFriendRequest(TEST_USER_ID, "테스터", 100L);

            // then
            verify(notificationRepository).save(any(Notification.class));
        }

        @Test
        @DisplayName("길드 초대 알림을 생성한다")
        void notifyGuildInvite_success() {
            // given
            NotificationPreference preference = createTestPreference(1L, TEST_USER_ID);
            Notification savedNotification = createTestNotification(1L, TEST_USER_ID, NotificationType.GUILD_INVITE);

            when(preferenceRepository.findByUserId(TEST_USER_ID)).thenReturn(Optional.of(preference));
            when(notificationRepository.save(any(Notification.class))).thenReturn(savedNotification);

            // when
            notificationService.notifyGuildInvite(TEST_USER_ID, "테스트 길드", 100L);

            // then
            verify(notificationRepository).save(any(Notification.class));
        }

        @Test
        @DisplayName("칭호 획득 알림을 생성한다")
        void notifyTitleAcquired_success() {
            // given
            NotificationPreference preference = createTestPreference(1L, TEST_USER_ID);
            preference.setSystemNotifications(true);  // ACHIEVEMENT 카테고리는 기본적으로 true로 처리
            Notification savedNotification = createTestNotification(1L, TEST_USER_ID, NotificationType.TITLE_ACQUIRED);

            when(notificationRepository.existsByUserIdAndReferenceTypeAndReferenceId(TEST_USER_ID, "TITLE", 1L))
                .thenReturn(false);
            when(preferenceRepository.findByUserId(TEST_USER_ID)).thenReturn(Optional.of(preference));
            when(notificationRepository.saveAndFlush(any(Notification.class))).thenReturn(savedNotification);

            // when
            notificationService.notifyTitleAcquired(TEST_USER_ID, 1L, "초보 모험가", "COMMON");

            // then
            verify(notificationRepository).saveAndFlush(any(Notification.class));
        }

        @Test
        @DisplayName("친구 수락 알림을 생성한다")
        void notifyFriendAccepted_success() {
            // given
            NotificationPreference preference = createTestPreference(1L, TEST_USER_ID);
            Notification savedNotification = createTestNotification(1L, TEST_USER_ID, NotificationType.FRIEND_ACCEPTED);

            when(preferenceRepository.findByUserId(TEST_USER_ID)).thenReturn(Optional.of(preference));
            when(notificationRepository.save(any(Notification.class))).thenReturn(savedNotification);

            // when
            notificationService.notifyFriendAccepted(TEST_USER_ID, "친구이름", 100L);

            // then
            verify(notificationRepository).save(any(Notification.class));
        }

        @Test
        @DisplayName("친구 거절 알림을 생성한다")
        void notifyFriendRejected_success() {
            // given
            NotificationPreference preference = createTestPreference(1L, TEST_USER_ID);
            Notification savedNotification = createTestNotification(1L, TEST_USER_ID, NotificationType.FRIEND_REJECTED);

            when(preferenceRepository.findByUserId(TEST_USER_ID)).thenReturn(Optional.of(preference));
            when(notificationRepository.save(any(Notification.class))).thenReturn(savedNotification);

            // when
            notificationService.notifyFriendRejected(TEST_USER_ID, "친구이름", 100L);

            // then
            verify(notificationRepository).save(any(Notification.class));
        }

        @Test
        @DisplayName("길드 가입 요청 알림을 생성한다")
        void notifyGuildJoinRequest_success() {
            // given
            NotificationPreference preference = createTestPreference(1L, TEST_USER_ID);
            Notification savedNotification = createTestNotification(1L, TEST_USER_ID, NotificationType.GUILD_JOIN_REQUEST);

            when(preferenceRepository.findByUserId(TEST_USER_ID)).thenReturn(Optional.of(preference));
            when(notificationRepository.save(any(Notification.class))).thenReturn(savedNotification);

            // when
            notificationService.notifyGuildJoinRequest(TEST_USER_ID, "테스트길드", 100L);

            // then
            verify(notificationRepository).save(any(Notification.class));
        }

        @Test
        @DisplayName("길드 가입 승인 알림을 생성한다")
        void notifyGuildJoinApproved_success() {
            // given
            NotificationPreference preference = createTestPreference(1L, TEST_USER_ID);
            Notification savedNotification = createTestNotification(1L, TEST_USER_ID, NotificationType.GUILD_JOIN_APPROVED);

            when(preferenceRepository.findByUserId(TEST_USER_ID)).thenReturn(Optional.of(preference));
            when(notificationRepository.save(any(Notification.class))).thenReturn(savedNotification);

            // when
            notificationService.notifyGuildJoinApproved(TEST_USER_ID, "테스트길드", 100L);

            // then
            verify(notificationRepository).save(any(Notification.class));
        }

        @Test
        @DisplayName("길드 가입 거절 알림을 생성한다")
        void notifyGuildJoinRejected_success() {
            // given
            NotificationPreference preference = createTestPreference(1L, TEST_USER_ID);
            Notification savedNotification = createTestNotification(1L, TEST_USER_ID, NotificationType.GUILD_JOIN_REJECTED);

            when(preferenceRepository.findByUserId(TEST_USER_ID)).thenReturn(Optional.of(preference));
            when(notificationRepository.save(any(Notification.class))).thenReturn(savedNotification);

            // when
            notificationService.notifyGuildJoinRejected(TEST_USER_ID, "테스트길드", 100L);

            // then
            verify(notificationRepository).save(any(Notification.class));
        }

        @Test
        @DisplayName("길드 미션 도착 알림을 생성한다")
        void notifyGuildMissionArrived_success() {
            // given
            NotificationPreference preference = createTestPreference(1L, TEST_USER_ID);
            Notification savedNotification = createTestNotification(1L, TEST_USER_ID, NotificationType.GUILD_MISSION_ARRIVED);

            when(preferenceRepository.findByUserId(TEST_USER_ID)).thenReturn(Optional.of(preference));
            when(notificationRepository.save(any(Notification.class))).thenReturn(savedNotification);

            // when
            notificationService.notifyGuildMissionArrived(TEST_USER_ID, "테스트길드", 100L);

            // then
            verify(notificationRepository).save(any(Notification.class));
        }

        @Test
        @DisplayName("길드 게시판 알림을 생성한다")
        void notifyGuildBulletin_success() {
            // given
            NotificationPreference preference = createTestPreference(1L, TEST_USER_ID);
            Notification savedNotification = createTestNotification(1L, TEST_USER_ID, NotificationType.GUILD_BULLETIN);

            when(preferenceRepository.findByUserId(TEST_USER_ID)).thenReturn(Optional.of(preference));
            when(notificationRepository.save(any(Notification.class))).thenReturn(savedNotification);

            // when
            notificationService.notifyGuildBulletin(TEST_USER_ID, "테스트길드", 100L, 1L, "게시글 제목");

            // then
            verify(notificationRepository).save(any(Notification.class));
        }

        @Test
        @DisplayName("길드 채팅 알림을 생성한다")
        void notifyGuildChat_success() {
            // given
            NotificationPreference preference = createTestPreference(1L, TEST_USER_ID);
            Notification savedNotification = createTestNotification(1L, TEST_USER_ID, NotificationType.GUILD_CHAT);

            when(preferenceRepository.findByUserId(TEST_USER_ID)).thenReturn(Optional.of(preference));
            when(notificationRepository.save(any(Notification.class))).thenReturn(savedNotification);

            // when
            notificationService.notifyGuildChat(TEST_USER_ID, "테스트길드", 100L, "작성자닉네임", 1L, "채팅 내용");

            // then
            verify(notificationRepository).save(any(Notification.class));
        }

        @Test
        @DisplayName("피드 댓글 알림을 생성한다")
        void notifyCommentOnMyFeed_success() {
            // given
            NotificationPreference preference = createTestPreference(1L, TEST_USER_ID);
            Notification savedNotification = createTestNotification(1L, TEST_USER_ID, NotificationType.COMMENT_ON_MY_FEED);

            when(preferenceRepository.findByUserId(TEST_USER_ID)).thenReturn(Optional.of(preference));
            when(notificationRepository.save(any(Notification.class))).thenReturn(savedNotification);

            // when
            notificationService.notifyCommentOnMyFeed(TEST_USER_ID, "댓글작성자", 100L);

            // then
            verify(notificationRepository).save(any(Notification.class));
        }

        @Test
        @DisplayName("업적 완료 알림을 생성한다")
        void notifyAchievementCompleted_success() {
            // given
            NotificationPreference preference = createTestPreference(1L, TEST_USER_ID);
            Notification savedNotification = createTestNotification(1L, TEST_USER_ID, NotificationType.ACHIEVEMENT_COMPLETED);

            when(notificationRepository.existsByUserIdAndReferenceTypeAndReferenceId(TEST_USER_ID, "ACHIEVEMENT", 1L))
                .thenReturn(false);
            when(preferenceRepository.findByUserId(TEST_USER_ID)).thenReturn(Optional.of(preference));
            when(notificationRepository.saveAndFlush(any(Notification.class))).thenReturn(savedNotification);

            // when
            notificationService.notifyAchievementCompleted(TEST_USER_ID, 1L, "미션 마스터");

            // then
            verify(notificationRepository).saveAndFlush(any(Notification.class));
        }

        @Test
        @DisplayName("콘텐츠 신고 알림을 생성한다")
        void notifyContentReported_success() {
            // given
            NotificationPreference preference = createTestPreference(1L, TEST_USER_ID);
            Notification savedNotification = createTestNotification(1L, TEST_USER_ID, NotificationType.CONTENT_REPORTED);

            when(preferenceRepository.findByUserId(TEST_USER_ID)).thenReturn(Optional.of(preference));
            when(notificationRepository.save(any(Notification.class))).thenReturn(savedNotification);

            // when
            notificationService.notifyContentReported(TEST_USER_ID, "피드");

            // then
            verify(notificationRepository).save(any(Notification.class));
        }

        @Test
        @DisplayName("길드 콘텐츠 신고 알림을 생성한다")
        void notifyGuildContentReported_success() {
            // given
            Long guildId = 100L;
            NotificationPreference preference = createTestPreference(1L, TEST_USER_ID);
            Notification savedNotification = createTestNotification(1L, TEST_USER_ID, NotificationType.CONTENT_REPORTED);

            when(preferenceRepository.findByUserId(TEST_USER_ID)).thenReturn(Optional.of(preference));
            when(notificationRepository.save(any(Notification.class))).thenReturn(savedNotification);

            // when
            notificationService.notifyGuildContentReported(TEST_USER_ID, "길드 공지", guildId);

            // then
            verify(notificationRepository).save(any(Notification.class));
        }
    }
}
