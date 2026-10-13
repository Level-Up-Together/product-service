package io.pinkspider.leveluptogethermvp.notificationservice.application;

import static io.pinkspider.global.test.TestReflectionUtils.setId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.pinkspider.global.enums.NotificationType;
import io.pinkspider.leveluptogethermvp.notificationservice.domain.entity.Notification;
import io.pinkspider.leveluptogethermvp.notificationservice.infrastructure.NotificationRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

/** LUT-539: dedup 타입 알림 insert 를 별도 트랜잭션(REQUIRES_NEW)에서 수행하는 전용 빈. */
@ExtendWith(MockitoExtension.class)
class NotificationCreatorTest {

    @Mock private NotificationRepository notificationRepository;

    @InjectMocks private NotificationCreator creator;

    @Test
    @DisplayName("알림 행을 즉시 flush 하며 생성하고 생성된 id 를 돌려준다 (호출자는 id 로 재조회)")
    void create_savesAndReturnsId() {
        when(notificationRepository.saveAndFlush(any(Notification.class)))
                .thenAnswer(
                        inv -> {
                            Notification n = inv.getArgument(0);
                            setId(n, 77L);
                            return n;
                        });

        Long id =
                creator.create(
                        "user-1",
                        NotificationType.TITLE_ACQUIRED,
                        "제목",
                        "메시지",
                        "TITLE",
                        5L,
                        "/mypage",
                        "rarity:COMMON");

        assertThat(id).isEqualTo(77L);
        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).saveAndFlush(captor.capture());
        Notification saved = captor.getValue();
        assertThat(saved.getUserId()).isEqualTo("user-1");
        assertThat(saved.getNotificationType()).isEqualTo(NotificationType.TITLE_ACQUIRED);
        assertThat(saved.getReferenceId()).isEqualTo(5L);
        assertThat(saved.getActionUrl()).isEqualTo("/mypage");
        assertThat(saved.getIconUrl()).isEqualTo("rarity:COMMON");
    }

    @Test
    @DisplayName("중복 키 예외는 삼키지 않고 호출자에게 전파한다")
    void create_propagatesDuplicateKey() {
        when(notificationRepository.saveAndFlush(any(Notification.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate"));

        assertThatThrownBy(
                        () ->
                                creator.create(
                                        "user-1",
                                        NotificationType.TITLE_ACQUIRED,
                                        "t",
                                        "m",
                                        null,
                                        1L,
                                        null,
                                        null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
