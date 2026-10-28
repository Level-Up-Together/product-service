package io.pinkspider.leveluptogethermvp.chatservice.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.pinkspider.global.exception.CustomException;
import io.pinkspider.global.facade.GuildQueryFacade;
import io.pinkspider.global.facade.UserQueryFacade;
import io.pinkspider.leveluptogethermvp.chatservice.domain.dto.ChatImageUploadResponse;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** LUT-590: 채팅·DM 이미지 업로드 권한, 전송 URL 검증, 삭제 후 정리 */
@ExtendWith(MockitoExtension.class)
class ChatImageServiceTest {

    private static final Long GUILD_ID = 1L;
    private static final String USER_ID = "sender";
    private static final String RECIPIENT_ID = "recipient";
    private static final String URL = "/uploads/chats/1/sender/a.jpg";

    @Mock private ChatImageStorageService storageService;
    @Mock private GuildQueryFacade guildQueryFacadeService;
    @Mock private UserQueryFacade userQueryFacadeService;

    @InjectMocks private ChatImageService chatImageService;

    private final MockMultipartFile image =
            new MockMultipartFile("image", "a.jpg", "image/jpeg", "x".getBytes());

    @Nested
    @DisplayName("길드 채팅 업로드")
    class GuildChatUpload {

        @Test
        @DisplayName("길드원이면 저장하고 URL 을 돌려준다")
        void success() {
            when(guildQueryFacadeService.guildExists(GUILD_ID)).thenReturn(true);
            when(guildQueryFacadeService.isActiveMember(GUILD_ID, USER_ID)).thenReturn(true);
            when(storageService.store(image, GUILD_ID, USER_ID)).thenReturn(URL);

            ChatImageUploadResponse response =
                    chatImageService.uploadGuildChatImage(GUILD_ID, USER_ID, image);

            assertThat(response.imageUrl()).isEqualTo(URL);
        }

        @Test
        @DisplayName("길드가 없으면 저장하지 않는다")
        void guildNotFound() {
            when(guildQueryFacadeService.guildExists(GUILD_ID)).thenReturn(false);

            assertThatThrownBy(
                            () -> chatImageService.uploadGuildChatImage(GUILD_ID, USER_ID, image))
                    .isInstanceOf(IllegalArgumentException.class);
            verify(storageService, never()).store(any(), any(), any());
        }

        @Test
        @DisplayName("길드원이 아니면 저장하지 않는다")
        void notMember() {
            when(guildQueryFacadeService.guildExists(GUILD_ID)).thenReturn(true);
            when(guildQueryFacadeService.isActiveMember(GUILD_ID, USER_ID)).thenReturn(false);

            assertThatThrownBy(
                            () -> chatImageService.uploadGuildChatImage(GUILD_ID, USER_ID, image))
                    .isInstanceOf(IllegalStateException.class);
            verify(storageService, never()).store(any(), any(), any());
        }
    }

    @Nested
    @DisplayName("DM 업로드")
    class DirectMessageUpload {

        private void bothMembers() {
            when(guildQueryFacadeService.guildExists(GUILD_ID)).thenReturn(true);
            when(guildQueryFacadeService.isActiveMember(GUILD_ID, USER_ID)).thenReturn(true);
            when(guildQueryFacadeService.isActiveMember(GUILD_ID, RECIPIENT_ID)).thenReturn(true);
        }

        @Test
        @DisplayName("둘 다 길드원이고 차단 관계가 없으면 저장한다")
        void success() {
            bothMembers();
            when(userQueryFacadeService.getBlockedUserIds(USER_ID)).thenReturn(List.of());
            when(storageService.store(image, GUILD_ID, USER_ID)).thenReturn(URL);

            assertThat(
                            chatImageService
                                    .uploadDirectMessageImage(
                                            GUILD_ID, USER_ID, RECIPIENT_ID, image)
                                    .imageUrl())
                    .isEqualTo(URL);
        }

        @Test
        @DisplayName("수신자가 길드원이 아니면 거부")
        void recipientNotMember() {
            when(guildQueryFacadeService.guildExists(GUILD_ID)).thenReturn(true);
            when(guildQueryFacadeService.isActiveMember(GUILD_ID, USER_ID)).thenReturn(true);
            when(guildQueryFacadeService.isActiveMember(GUILD_ID, RECIPIENT_ID)).thenReturn(false);

            assertThatThrownBy(
                            () ->
                                    chatImageService.uploadDirectMessageImage(
                                            GUILD_ID, USER_ID, RECIPIENT_ID, image))
                    .isInstanceOf(IllegalStateException.class);
            verify(storageService, never()).store(any(), any(), any());
        }

        @Test
        @DisplayName("발신자가 상대를 차단했으면 error.dm.blocked_user 로 거부")
        void senderBlocksRecipient() {
            bothMembers();
            when(userQueryFacadeService.getBlockedUserIds(USER_ID))
                    .thenReturn(List.of(RECIPIENT_ID));

            assertThatThrownBy(
                            () ->
                                    chatImageService.uploadDirectMessageImage(
                                            GUILD_ID, USER_ID, RECIPIENT_ID, image))
                    .isInstanceOf(CustomException.class)
                    .hasMessageContaining("error.dm.blocked_user");
            verify(storageService, never()).store(any(), any(), any());
        }
    }

    @Nested
    @DisplayName("전송 image_url 검증")
    class ValidateImageUrl {

        @Test
        @DisplayName("비어 있으면(텍스트 메시지) 통과")
        void blank() {
            assertThatCode(() -> chatImageService.validateImageUrl(null, GUILD_ID, USER_ID))
                    .doesNotThrowAnyException();
            assertThatCode(() -> chatImageService.validateImageUrl("", GUILD_ID, USER_ID))
                    .doesNotThrowAnyException();
            verify(storageService, never()).isOwnedBy(anyString(), any(), any());
        }

        @Test
        @DisplayName("보낸 사람 본인이 이 길드용으로 올린 URL 이면 통과")
        void owned() {
            when(storageService.isOwnedBy(URL, GUILD_ID, USER_ID)).thenReturn(true);

            assertThatCode(() -> chatImageService.validateImageUrl(URL, GUILD_ID, USER_ID))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("외부 URL 은 error.chat.invalid_image_url 로 거부")
        void foreign() {
            String evil = "https://evil.example.com/t.gif";
            when(storageService.isOwnedBy(evil, GUILD_ID, USER_ID)).thenReturn(false);

            assertThatThrownBy(() -> chatImageService.validateImageUrl(evil, GUILD_ID, USER_ID))
                    .isInstanceOf(CustomException.class)
                    .hasMessageContaining("error.chat.invalid_image_url");
        }

        @Test
        @DisplayName("남이 올린 채팅 사진 URL 로는 보낼 수 없다 (삭제로 남의 파일을 지우는 공격 차단)")
        void othersUpload() {
            String othersUrl = "/uploads/chats/1/victim/a.jpg";
            when(storageService.isOwnedBy(othersUrl, GUILD_ID, USER_ID)).thenReturn(false);

            assertThatThrownBy(
                            () -> chatImageService.validateImageUrl(othersUrl, GUILD_ID, USER_ID))
                    .isInstanceOf(CustomException.class)
                    .hasMessageContaining("error.chat.invalid_image_url");
        }
    }

    @Nested
    @DisplayName("삭제 후 이미지 정리")
    class DeleteAfterCommit {

        @AfterEach
        void clearSync() {
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.clearSynchronization();
            }
        }

        @Test
        @DisplayName("이미지가 없으면 아무것도 하지 않는다")
        void noImage() {
            chatImageService.deleteAfterCommit(null, GUILD_ID, USER_ID);
            chatImageService.deleteAfterCommit("", GUILD_ID, USER_ID);

            verify(storageService, never()).delete(anyString());
        }

        @Test
        @DisplayName("트랜잭션 밖이면 즉시 지운다")
        void outsideTransaction() {
            when(storageService.isOwnedBy(URL, GUILD_ID, USER_ID)).thenReturn(true);

            chatImageService.deleteAfterCommit(URL, GUILD_ID, USER_ID);

            verify(storageService).delete(URL);
        }

        @Test
        @DisplayName("트랜잭션 안이면 커밋 후에만 지운다")
        void insideTransaction() {
            when(storageService.isOwnedBy(URL, GUILD_ID, USER_ID)).thenReturn(true);
            TransactionSynchronizationManager.initSynchronization();

            chatImageService.deleteAfterCommit(URL, GUILD_ID, USER_ID);
            verify(storageService, never()).delete(anyString());

            List<TransactionSynchronization> syncs =
                    TransactionSynchronizationManager.getSynchronizations();
            assertThat(syncs).hasSize(1);
            syncs.get(0).afterCommit();
            verify(storageService).delete(URL);
        }

        @Test
        @DisplayName("발신자 본인 경로가 아닌 파일은 지우지 않는다 (검증 도입 전 저장된 메시지 대비)")
        void notSendersFile_skipped() {
            String othersUrl = "/uploads/chats/1/victim/a.jpg";
            when(storageService.isOwnedBy(othersUrl, GUILD_ID, USER_ID)).thenReturn(false);

            chatImageService.deleteAfterCommit(othersUrl, GUILD_ID, USER_ID);

            verify(storageService, never()).delete(anyString());
        }
    }
}
