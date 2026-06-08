package io.pinkspider.leveluptogethermvp.notificationservice.application;

import static io.pinkspider.global.test.TestReflectionUtils.setId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.pinkspider.leveluptogethermvp.notificationservice.domain.dto.NotificationPreferenceRequest;
import io.pinkspider.leveluptogethermvp.notificationservice.domain.dto.NotificationPreferenceResponse;
import io.pinkspider.leveluptogethermvp.notificationservice.domain.dto.NotificationResponse;
import io.pinkspider.leveluptogethermvp.notificationservice.domain.dto.NotificationSummaryResponse;
import io.pinkspider.leveluptogethermvp.notificationservice.domain.entity.Notification;
import io.pinkspider.leveluptogethermvp.notificationservice.domain.entity.NotificationPreference;
import io.pinkspider.global.enums.NotificationType;
import io.pinkspider.global.messaging.producer.AppPushMessageProducer;
import io.pinkspider.leveluptogethermvp.notificationservice.infrastructure.NotificationPreferenceRepository;
import io.pinkspider.leveluptogethermvp.notificationservice.infrastructure.NotificationRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
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

    @Mock
    private AppPushMessageProducer appPushMessageProducer;

    @Mock
    private DeviceTokenService deviceTokenService;

    @Mock
    private org.springframework.context.MessageSource messageSource;

    @Mock
    private io.pinkspider.leveluptogethermvp.userservice.unit.user.infrastructure.UserRepository userRepository;

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
    @DisplayName("sendNotification 테스트")
    class SendNotificationTest {

        @Test
        @DisplayName("일반 타입 알림을 enum 메타데이터로 생성한다")
        void sendNotification_normalType_usesEnumMetadata() {
            // given
            NotificationPreference preference = createTestPreference(1L, TEST_USER_ID);
            Notification savedNotification = createTestNotification(1L, TEST_USER_ID, NotificationType.FRIEND_REQUEST);

            when(preferenceRepository.findByUserId(TEST_USER_ID)).thenReturn(Optional.of(preference));
            when(notificationRepository.save(any(Notification.class))).thenReturn(savedNotification);

            // when
            notificationService.sendNotification(TEST_USER_ID, NotificationType.FRIEND_REQUEST,
                100L, null, "테스터");

            // then
            verify(notificationRepository).save(any(Notification.class));
            verify(notificationRepository, never()).saveAndFlush(any());
        }

        @Test
        @DisplayName("중복 방지 타입은 saveAndFlush를 사용한다")
        void sendNotification_dedupType_usesSaveAndFlush() {
            // given
            NotificationPreference preference = createTestPreference(1L, TEST_USER_ID);
            Notification savedNotification = createTestNotification(1L, TEST_USER_ID, NotificationType.TITLE_ACQUIRED);

            when(notificationRepository.existsByUserIdAndNotificationTypeAndReferenceId(
                TEST_USER_ID, NotificationType.TITLE_ACQUIRED, 1L)).thenReturn(false);
            when(preferenceRepository.findByUserId(TEST_USER_ID)).thenReturn(Optional.of(preference));
            when(notificationRepository.saveAndFlush(any(Notification.class))).thenReturn(savedNotification);

            // when
            notificationService.sendNotification(TEST_USER_ID, NotificationType.TITLE_ACQUIRED,
                1L, "rarity:COMMON", "초보 모험가");

            // then
            verify(notificationRepository).saveAndFlush(any(Notification.class));
            verify(notificationRepository, never()).save(any(Notification.class));
        }

        @Test
        @DisplayName("중복 알림이 이미 존재하면 스킵한다")
        void sendNotification_duplicateExists_skips() {
            // given
            when(notificationRepository.existsByUserIdAndNotificationTypeAndReferenceId(
                TEST_USER_ID, NotificationType.ACHIEVEMENT_COMPLETED, 1L)).thenReturn(true);

            // when
            notificationService.sendNotification(TEST_USER_ID, NotificationType.ACHIEVEMENT_COMPLETED,
                1L, null, "미션 마스터");

            // then
            verify(notificationRepository, never()).save(any());
            verify(notificationRepository, never()).saveAndFlush(any());
        }

        @Test
        @DisplayName("DataIntegrityViolationException 발생 시 무시한다")
        void sendNotification_dataIntegrityViolation_ignored() {
            // given
            when(notificationRepository.existsByUserIdAndNotificationTypeAndReferenceId(
                TEST_USER_ID, NotificationType.TITLE_ACQUIRED, 1L)).thenReturn(false);
            NotificationPreference preference = createTestPreference(1L, TEST_USER_ID);
            when(preferenceRepository.findByUserId(TEST_USER_ID)).thenReturn(Optional.of(preference));
            when(notificationRepository.saveAndFlush(any(Notification.class)))
                .thenThrow(new DataIntegrityViolationException("Duplicate entry"));

            // when & then - 예외 발생하지 않음
            notificationService.sendNotification(TEST_USER_ID, NotificationType.TITLE_ACQUIRED,
                1L, "rarity:LEGENDARY", "전설적인 모험가");
        }

        @Test
        @DisplayName("길드 초대 알림을 생성한다")
        void sendNotification_guildInvite_success() {
            // given
            NotificationPreference preference = createTestPreference(1L, TEST_USER_ID);
            Notification savedNotification = createTestNotification(1L, TEST_USER_ID, NotificationType.GUILD_INVITE);

            when(preferenceRepository.findByUserId(TEST_USER_ID)).thenReturn(Optional.of(preference));
            when(notificationRepository.save(any(Notification.class))).thenReturn(savedNotification);

            // when
            notificationService.sendNotification(TEST_USER_ID, NotificationType.GUILD_INVITE,
                1L, null, "마스터닉네임", "테스트 길드");

            // then
            verify(notificationRepository).save(any(Notification.class));
        }

        @Test
        @DisplayName("카테고리 비활성화 시 알림을 생성하지 않는다")
        void sendNotification_categoryDisabled_skips() {
            // given
            NotificationPreference preference = NotificationPreference.builder()
                .userId(TEST_USER_ID)
                .friendNotifications(false)
                .build();
            setId(preference, 1L);

            when(preferenceRepository.findByUserId(TEST_USER_ID)).thenReturn(Optional.of(preference));

            // when
            notificationService.sendNotification(TEST_USER_ID, NotificationType.FRIEND_REQUEST,
                100L, null, "테스터");

            // then
            verify(notificationRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("편의 메서드 테스트")
    class ConvenienceMethodsTest {

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

    @Nested
    @DisplayName("pushEnabled + quietHours 분기 테스트")
    class PushEnabledQuietHoursTest {

        @Test
        @DisplayName("pushEnabled가 false이면 푸시 알림을 전송하지 않는다")
        void createNotification_pushDisabled_doesNotSendPush() {
            // given
            NotificationPreference preference = NotificationPreference.builder()
                .userId(TEST_USER_ID)
                .pushEnabled(false)
                .friendNotifications(true)
                .guildNotifications(true)
                .socialNotifications(true)
                .systemNotifications(true)
                .quietHoursEnabled(false)
                .build();
            setId(preference, 1L);

            Notification savedNotification = createTestNotification(1L, TEST_USER_ID, NotificationType.SYSTEM);

            when(preferenceRepository.findByUserId(TEST_USER_ID)).thenReturn(Optional.of(preference));
            when(notificationRepository.save(any(Notification.class))).thenReturn(savedNotification);

            // when
            NotificationResponse result = notificationService.createNotification(
                TEST_USER_ID, NotificationType.SYSTEM, "제목", "내용");

            // then
            assertThat(result).isNotNull();
            verify(appPushMessageProducer, org.mockito.Mockito.never()).sendMessage(any());
        }

        @Test
        @DisplayName("pushEnabled가 true이고 quietHours 미설정이면 푸시 알림을 전송한다")
        void createNotification_pushEnabled_noQuietHours_sendsPush() {
            // given
            NotificationPreference preference = createTestPreference(1L, TEST_USER_ID);
            Notification savedNotification = createTestNotification(1L, TEST_USER_ID, NotificationType.SYSTEM);

            when(preferenceRepository.findByUserId(TEST_USER_ID)).thenReturn(Optional.of(preference));
            when(notificationRepository.save(any(Notification.class))).thenReturn(savedNotification);

            // when
            NotificationResponse result = notificationService.createNotification(
                TEST_USER_ID, NotificationType.SYSTEM, "제목", "내용");

            // then
            assertThat(result).isNotNull();
            verify(appPushMessageProducer).sendMessage(any());
        }

        @Test
        @DisplayName("quietHoursEnabled=true이지만 start/end가 null이면 푸시 알림을 전송한다")
        void createNotification_quietHoursEnabled_nullStartEnd_sendsPush() {
            // given
            NotificationPreference preference = NotificationPreference.builder()
                .userId(TEST_USER_ID)
                .pushEnabled(true)
                .friendNotifications(true)
                .guildNotifications(true)
                .socialNotifications(true)
                .systemNotifications(true)
                .quietHoursEnabled(true)
                .quietHoursStart(null)
                .quietHoursEnd(null)
                .build();
            setId(preference, 1L);

            Notification savedNotification = createTestNotification(1L, TEST_USER_ID, NotificationType.SYSTEM);

            when(preferenceRepository.findByUserId(TEST_USER_ID)).thenReturn(Optional.of(preference));
            when(notificationRepository.save(any(Notification.class))).thenReturn(savedNotification);

            // when
            NotificationResponse result = notificationService.createNotification(
                TEST_USER_ID, NotificationType.SYSTEM, "제목", "내용");

            // then
            assertThat(result).isNotNull();
            // quietHours가 활성화되어도 start/end가 null이므로 quiet hours 아님 → 푸시 전송됨
            verify(appPushMessageProducer).sendMessage(any());
        }

        @Test
        @DisplayName("사용자가 존재하고 timezone이 설정되어 있으면 해당 timezone으로 quiet hours 판단한다")
        void createNotification_userWithTimezone_quietHoursCheck() {
            // given
            io.pinkspider.leveluptogethermvp.userservice.unit.user.domain.entity.Users user =
                io.pinkspider.leveluptogethermvp.userservice.unit.user.domain.entity.Users.builder()
                    .id(TEST_USER_ID)
                    .email("test@example.com")
                    .nickname("testNick")
                    .provider("google")
                    .preferredTimezone("Asia/Seoul")
                    .build();

            NotificationPreference preference = NotificationPreference.builder()
                .userId(TEST_USER_ID)
                .pushEnabled(true)
                .friendNotifications(true)
                .guildNotifications(true)
                .socialNotifications(true)
                .systemNotifications(true)
                .quietHoursEnabled(true)
                .quietHoursStart("02:00")
                .quietHoursEnd("04:00")
                .build();
            setId(preference, 1L);

            Notification savedNotification = createTestNotification(1L, TEST_USER_ID, NotificationType.SYSTEM);

            when(preferenceRepository.findByUserId(TEST_USER_ID)).thenReturn(Optional.of(preference));
            when(notificationRepository.save(any(Notification.class))).thenReturn(savedNotification);
            when(userRepository.findById(TEST_USER_ID)).thenReturn(Optional.of(user));

            // when
            NotificationResponse result = notificationService.createNotification(
                TEST_USER_ID, NotificationType.SYSTEM, "제목", "내용");

            // then
            assertThat(result).isNotNull();
            // 결과는 현재 시간에 따라 다르지만 예외 없이 실행되어야 함
        }
    }

    @Nested
    @DisplayName("updatePreferences 추가 분기 테스트")
    class UpdatePreferencesExtraTest {

        @Test
        @DisplayName("guildNotifications, socialNotifications, systemNotifications도 업데이트된다")
        void updatePreferences_allFields_success() {
            // given
            NotificationPreference preference = createTestPreference(1L, TEST_USER_ID);
            NotificationPreferenceRequest request = NotificationPreferenceRequest.builder()
                .pushEnabled(false)
                .friendNotifications(false)
                .guildNotifications(false)
                .socialNotifications(false)
                .systemNotifications(false)
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
            assertThat(preference.getGuildNotifications()).isFalse();
            assertThat(preference.getSocialNotifications()).isFalse();
            assertThat(preference.getSystemNotifications()).isFalse();
            assertThat(preference.getQuietHoursEnabled()).isTrue();
            assertThat(preference.getQuietHoursStart()).isEqualTo("22:00");
            assertThat(preference.getQuietHoursEnd()).isEqualTo("08:00");
        }
    }

    @Nested
    @DisplayName("createNotification iconUrl 포함 테스트")
    class CreateNotificationWithIconUrlTest {

        @Test
        @DisplayName("iconUrl을 포함하여 알림을 생성한다")
        void createNotification_withIconUrl_success() {
            // given
            NotificationPreference preference = createTestPreference(1L, TEST_USER_ID);
            Notification savedNotification = Notification.builder()
                .userId(TEST_USER_ID)
                .notificationType(NotificationType.TITLE_ACQUIRED)
                .title("칭호 획득")
                .message("새 칭호를 획득했습니다")
                .iconUrl("rarity:LEGENDARY")
                .build();
            setId(savedNotification, 1L);

            when(preferenceRepository.findByUserId(TEST_USER_ID)).thenReturn(Optional.of(preference));
            when(notificationRepository.save(any(Notification.class))).thenReturn(savedNotification);

            // when
            NotificationResponse result = notificationService.createNotification(
                TEST_USER_ID, NotificationType.TITLE_ACQUIRED, "칭호 획득",
                "새 칭호를 획득했습니다", "TITLE", 1L, "/achievement", "rarity:LEGENDARY");

            // then
            assertThat(result).isNotNull();
            assertThat(result.getIconUrl()).isEqualTo("rarity:LEGENDARY");
        }
    }

    @Nested
    @DisplayName("saveInquiryRepliedInApp 테스트")
    class SaveInquiryRepliedInAppTest {

        @Test
        @DisplayName("1:1 문의 답변 in-app 알림을 저장한다")
        void saveInquiryRepliedInApp_success() {
            // given
            NotificationPreference preference = createTestPreference(1L, TEST_USER_ID);
            Notification savedNotification = createTestNotification(1L, TEST_USER_ID, NotificationType.INQUIRY_REPLIED);

            when(preferenceRepository.findByUserId(TEST_USER_ID)).thenReturn(Optional.of(preference));
            when(notificationRepository.save(any(Notification.class))).thenReturn(savedNotification);

            // when
            notificationService.saveInquiryRepliedInApp(TEST_USER_ID, 10L, "서비스 문의");

            // then
            verify(notificationRepository).save(any(Notification.class));
        }

        @Test
        @DisplayName("userId가 null이면 저장하지 않는다")
        void saveInquiryRepliedInApp_nullUserId_skips() {
            // when
            notificationService.saveInquiryRepliedInApp(null, 10L, "서비스 문의");

            // then
            verify(notificationRepository, never()).save(any());
        }

        @Test
        @DisplayName("userId가 빈 문자열이면 저장하지 않는다")
        void saveInquiryRepliedInApp_blankUserId_skips() {
            // when
            notificationService.saveInquiryRepliedInApp("   ", 10L, "서비스 문의");

            // then
            verify(notificationRepository, never()).save(any());
        }

        @Test
        @DisplayName("inquiryTitle이 null이어도 저장한다")
        void saveInquiryRepliedInApp_nullTitle_success() {
            // given
            NotificationPreference preference = createTestPreference(1L, TEST_USER_ID);
            Notification savedNotification = createTestNotification(1L, TEST_USER_ID, NotificationType.INQUIRY_REPLIED);

            when(preferenceRepository.findByUserId(TEST_USER_ID)).thenReturn(Optional.of(preference));
            when(notificationRepository.save(any(Notification.class))).thenReturn(savedNotification);

            // when
            notificationService.saveInquiryRepliedInApp(TEST_USER_ID, 10L, null);

            // then
            verify(notificationRepository).save(any(Notification.class));
        }
    }

    @Nested
    @DisplayName("localizePushText 테스트")
    class LocalizePushTextTest {

        @Test
        @DisplayName("사용자 locale로 푸시 텍스트를 현지화한다")
        void localizePushText_success() {
            // given
            io.pinkspider.leveluptogethermvp.userservice.unit.user.domain.entity.Users user =
                io.pinkspider.leveluptogethermvp.userservice.unit.user.domain.entity.Users.builder()
                    .id(TEST_USER_ID)
                    .email("test@example.com")
                    .nickname("testNick")
                    .provider("google")
                    .preferredLocale("ko")
                    .build();
            when(userRepository.findById(TEST_USER_ID)).thenReturn(Optional.of(user));

            // when
            String[] result = notificationService.localizePushText(TEST_USER_ID, NotificationType.FRIEND_REQUEST, "닉네임");

            // then
            assertThat(result).hasSize(2);
            // 첫 번째 요소: title, 두 번째: message
        }

        @Test
        @DisplayName("사용자가 없으면 기본 locale(en)로 처리한다")
        void localizePushText_userNotFound_usesDefaultLocale() {
            // given
            when(userRepository.findById(TEST_USER_ID)).thenReturn(Optional.empty());

            // when
            String[] result = notificationService.localizePushText(TEST_USER_ID, NotificationType.SYSTEM);

            // then
            assertThat(result).hasSize(2);
        }
    }

    @Nested
    @DisplayName("quiet hours 야간범위(cross-midnight) 테스트")
    class QuietHoursNightRangeTest {

        @Test
        @DisplayName("야간 quiet hours(22:00~06:00) 내이면 푸시를 보내지 않는다")
        void createNotification_quietHoursNightRange_pushSkipped() {
            // given
            io.pinkspider.leveluptogethermvp.userservice.unit.user.domain.entity.Users user =
                io.pinkspider.leveluptogethermvp.userservice.unit.user.domain.entity.Users.builder()
                    .id(TEST_USER_ID)
                    .email("test@example.com")
                    .nickname("testNick")
                    .provider("google")
                    .preferredTimezone("Asia/Seoul")
                    .build();

            // start > end (야간 범위) → 현재 시간이 어디든 예외 없이 실행
            NotificationPreference preference = NotificationPreference.builder()
                .userId(TEST_USER_ID)
                .pushEnabled(true)
                .friendNotifications(true)
                .guildNotifications(true)
                .socialNotifications(true)
                .systemNotifications(true)
                .quietHoursEnabled(true)
                .quietHoursStart("00:00")  // 항상 quiet에 걸리도록 넓은 범위
                .quietHoursEnd("23:59")
                .build();
            setId(preference, 1L);

            Notification savedNotification = createTestNotification(1L, TEST_USER_ID, NotificationType.SYSTEM);

            when(preferenceRepository.findByUserId(TEST_USER_ID)).thenReturn(Optional.of(preference));
            when(notificationRepository.save(any(Notification.class))).thenReturn(savedNotification);
            when(userRepository.findById(TEST_USER_ID)).thenReturn(Optional.of(user));

            // when
            NotificationResponse result = notificationService.createNotification(
                TEST_USER_ID, NotificationType.SYSTEM, "제목", "내용");

            // then
            assertThat(result).isNotNull();
            // quiet hours 내에 있어 푸시 전송 안 됨 (예외 없이 정상 처리)
        }

        @Test
        @DisplayName("잘못된 timezone 문자열이어도 예외 없이 처리한다")
        void createNotification_invalidTimezone_handledGracefully() {
            // given
            io.pinkspider.leveluptogethermvp.userservice.unit.user.domain.entity.Users user =
                io.pinkspider.leveluptogethermvp.userservice.unit.user.domain.entity.Users.builder()
                    .id(TEST_USER_ID)
                    .email("test@example.com")
                    .nickname("testNick")
                    .provider("google")
                    .preferredTimezone("Invalid/Zone")  // 잘못된 timezone
                    .build();

            NotificationPreference preference = NotificationPreference.builder()
                .userId(TEST_USER_ID)
                .pushEnabled(true)
                .systemNotifications(true)
                .quietHoursEnabled(true)
                .quietHoursStart("22:00")
                .quietHoursEnd("08:00")
                .build();
            setId(preference, 1L);

            Notification savedNotification = createTestNotification(1L, TEST_USER_ID, NotificationType.SYSTEM);
            when(preferenceRepository.findByUserId(TEST_USER_ID)).thenReturn(Optional.of(preference));
            when(notificationRepository.save(any(Notification.class))).thenReturn(savedNotification);
            when(userRepository.findById(TEST_USER_ID)).thenReturn(Optional.of(user));

            // when - 예외 없이 실행
            NotificationResponse result = notificationService.createNotification(
                TEST_USER_ID, NotificationType.SYSTEM, "제목", "내용");

            // then
            assertThat(result).isNotNull();
        }
    }

    @Nested
    @DisplayName("markAllAsRead 배지 동기화 테스트")
    class MarkAllAsReadBadgeTest {

        @Test
        @DisplayName("모든 알림 읽음 처리 시 배지 카운트가 0으로 동기화된다")
        void markAllAsRead_syncBadgeToZero() {
            // given
            when(notificationRepository.markAllAsRead(TEST_USER_ID)).thenReturn(3);

            // when
            int result = notificationService.markAllAsRead(TEST_USER_ID);

            // then
            assertThat(result).isEqualTo(3);
            verify(deviceTokenService).syncBadgeCount(TEST_USER_ID, 0);
        }

        @Test
        @DisplayName("읽음 처리할 알림이 없으면 0을 반환한다")
        void markAllAsRead_noNotifications_returnsZero() {
            // given
            when(notificationRepository.markAllAsRead(TEST_USER_ID)).thenReturn(0);

            // when
            int result = notificationService.markAllAsRead(TEST_USER_ID);

            // then
            assertThat(result).isZero();
            verify(deviceTokenService).syncBadgeCount(TEST_USER_ID, 0);
        }
    }

    @Nested
    @DisplayName("markAsRead 배지 동기화 테스트")
    class MarkAsReadBadgeSyncTest {

        @Test
        @DisplayName("알림 읽음 처리 후 남은 미읽음 수로 배지를 동기화한다")
        void markAsRead_syncBadgeCount() {
            // given
            Long notificationId = 1L;
            Notification notification = createTestNotification(notificationId, TEST_USER_ID, NotificationType.SYSTEM);
            when(notificationRepository.findById(notificationId)).thenReturn(Optional.of(notification));
            when(notificationRepository.countUnreadByUserId(TEST_USER_ID)).thenReturn(2);

            // when
            notificationService.markAsRead(TEST_USER_ID, notificationId);

            // then
            verify(deviceTokenService).syncBadgeCount(TEST_USER_ID, 2);
        }
    }

    @Nested
    @DisplayName("sendNotification 야간범위 quiet hours 테스트")
    class SendNotificationQuietHoursTest {

        @Test
        @DisplayName("sendNotification에서도 카테고리 활성화 시 push가 전송된다")
        void sendNotification_pushEnabledNoQuietHours_pushSent() {
            // given
            NotificationPreference preference = createTestPreference(1L, TEST_USER_ID);
            Notification savedNotification = createTestNotification(1L, TEST_USER_ID, NotificationType.SYSTEM);

            when(preferenceRepository.findByUserId(TEST_USER_ID)).thenReturn(Optional.of(preference));
            when(notificationRepository.save(any(Notification.class))).thenReturn(savedNotification);

            // when
            notificationService.sendNotification(TEST_USER_ID, NotificationType.SYSTEM,
                null, null);

            // then
            verify(notificationRepository).save(any(Notification.class));
            verify(appPushMessageProducer).sendMessage(any());
        }
    }

    @Nested
    @DisplayName("cleanupExpiredNotifications 0건 처리 테스트")
    class CleanupExpiredNotificationsZeroTest {

        @Test
        @DisplayName("만료된 알림이 없으면 0을 반환한다")
        void cleanupExpiredNotifications_noExpired_returnsZero() {
            // given
            when(notificationRepository.deleteExpiredNotifications(any(LocalDateTime.class))).thenReturn(0);

            // when
            int result = notificationService.cleanupExpiredNotifications();

            // then
            assertThat(result).isZero();
        }
    }
}
