package io.pinkspider.leveluptogethermvp.chatservice.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.pinkspider.leveluptogethermvp.chatservice.domain.dto.DirectConversationResponse;
import io.pinkspider.leveluptogethermvp.chatservice.domain.dto.DirectMessageRequest;
import io.pinkspider.leveluptogethermvp.chatservice.domain.dto.DirectMessageResponse;
import io.pinkspider.leveluptogethermvp.chatservice.domain.entity.GuildDirectConversation;
import io.pinkspider.leveluptogethermvp.chatservice.domain.entity.GuildDirectMessage;
import io.pinkspider.leveluptogethermvp.chatservice.infrastructure.GuildDirectConversationRepository;
import io.pinkspider.leveluptogethermvp.chatservice.infrastructure.GuildDirectMessageRepository;
import io.pinkspider.leveluptogethermvp.guildservice.infrastructure.GuildMemberRepository;
import io.pinkspider.leveluptogethermvp.guildservice.infrastructure.GuildRepository;
import io.pinkspider.leveluptogethermvp.notificationservice.application.FcmPushService;
import io.pinkspider.leveluptogethermvp.userservice.profile.application.UserProfileCacheService;
import io.pinkspider.leveluptogethermvp.userservice.profile.domain.dto.UserProfileCache;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
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
class GuildDirectMessageServiceTest {

    @Mock
    private GuildDirectConversationRepository conversationRepository;

    @Mock
    private GuildDirectMessageRepository messageRepository;

    @Mock
    private GuildMemberRepository memberRepository;

    @Mock
    private UserProfileCacheService userProfileCacheService;

    @Mock
    private GuildRepository guildRepository;

    @Mock
    private FcmPushService fcmPushService;

    @InjectMocks
    private GuildDirectMessageService dmService;

    private GuildDirectConversation testConversation;
    private GuildDirectMessage testMessage;

    private static final String USER_ID_1 = "user-aaa";  // 알파벳순 앞
    private static final String USER_ID_2 = "user-bbb";  // 알파벳순 뒤
    private static final String NICKNAME_1 = "유저1";
    private static final String NICKNAME_2 = "유저2";

    @BeforeEach
    void setUp() {
        testConversation = GuildDirectConversation.create(1L, USER_ID_1, USER_ID_2);
        setId(testConversation, GuildDirectConversation.class, 1L);

        testMessage = GuildDirectMessage.createTextMessage(testConversation, USER_ID_1, NICKNAME_1, "테스트 메시지");
        setId(testMessage, GuildDirectMessage.class, 1L);
    }

    private <T> void setId(T entity, Class<T> clazz, Long id) {
        try {
            java.lang.reflect.Field idField = clazz.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(entity, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private <T> void setStringId(T entity, Class<T> clazz, String id) {
        try {
            java.lang.reflect.Field idField = clazz.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(entity, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Nested
    @DisplayName("DM 전송 테스트")
    class SendMessageTest {

        @Test
        @DisplayName("텍스트 DM을 전송한다")
        void sendMessage_text_success() {
            // given
            DirectMessageRequest request = DirectMessageRequest.builder()
                .content("안녕하세요!")
                .build();

            when(guildRepository.existsByIdAndIsActiveTrue(1L)).thenReturn(true);
            when(memberRepository.isActiveMember(1L, USER_ID_1)).thenReturn(true);
            when(memberRepository.isActiveMember(1L, USER_ID_2)).thenReturn(true);
            when(userProfileCacheService.getUserNickname(USER_ID_1)).thenReturn(NICKNAME_1);
            when(conversationRepository.findConversation(1L, USER_ID_1, USER_ID_2))
                .thenReturn(Optional.of(testConversation));
            when(messageRepository.save(any(GuildDirectMessage.class))).thenAnswer(inv -> {
                GuildDirectMessage msg = inv.getArgument(0);
                setId(msg, GuildDirectMessage.class, 1L);
                return msg;
            });

            // when
            DirectMessageResponse response = dmService.sendMessage(1L, USER_ID_1, USER_ID_2, request);

            // then
            assertThat(response).isNotNull();
            assertThat(response.getContent()).isEqualTo("안녕하세요!");
            assertThat(response.getSenderId()).isEqualTo(USER_ID_1);
            verify(messageRepository).save(any(GuildDirectMessage.class));
        }

        @Test
        @DisplayName("이미지 DM을 전송한다")
        void sendMessage_image_success() {
            // given
            DirectMessageRequest request = DirectMessageRequest.builder()
                .content("이미지입니다")
                .imageUrl("https://example.com/image.jpg")
                .build();

            when(guildRepository.existsByIdAndIsActiveTrue(1L)).thenReturn(true);
            when(memberRepository.isActiveMember(1L, USER_ID_1)).thenReturn(true);
            when(memberRepository.isActiveMember(1L, USER_ID_2)).thenReturn(true);
            when(userProfileCacheService.getUserNickname(USER_ID_1)).thenReturn(NICKNAME_1);
            when(conversationRepository.findConversation(1L, USER_ID_1, USER_ID_2))
                .thenReturn(Optional.of(testConversation));
            when(messageRepository.save(any(GuildDirectMessage.class))).thenAnswer(inv -> {
                GuildDirectMessage msg = inv.getArgument(0);
                setId(msg, GuildDirectMessage.class, 1L);
                return msg;
            });

            // when
            DirectMessageResponse response = dmService.sendMessage(1L, USER_ID_1, USER_ID_2, request);

            // then
            assertThat(response).isNotNull();
            assertThat(response.getImageUrl()).isEqualTo("https://example.com/image.jpg");
        }

        @Test
        @DisplayName("새 대화가 없으면 생성한다")
        void sendMessage_createNewConversation() {
            // given
            DirectMessageRequest request = DirectMessageRequest.builder()
                .content("첫 메시지!")
                .build();

            when(guildRepository.existsByIdAndIsActiveTrue(1L)).thenReturn(true);
            when(memberRepository.isActiveMember(1L, USER_ID_1)).thenReturn(true);
            when(memberRepository.isActiveMember(1L, USER_ID_2)).thenReturn(true);
            when(userProfileCacheService.getUserNickname(USER_ID_1)).thenReturn(NICKNAME_1);
            when(conversationRepository.findConversation(1L, USER_ID_1, USER_ID_2))
                .thenReturn(Optional.empty());
            when(conversationRepository.save(any(GuildDirectConversation.class))).thenAnswer(inv -> {
                GuildDirectConversation conv = inv.getArgument(0);
                setId(conv, GuildDirectConversation.class, 2L);
                return conv;
            });
            when(messageRepository.save(any(GuildDirectMessage.class))).thenAnswer(inv -> {
                GuildDirectMessage msg = inv.getArgument(0);
                setId(msg, GuildDirectMessage.class, 1L);
                return msg;
            });

            // when
            DirectMessageResponse response = dmService.sendMessage(1L, USER_ID_1, USER_ID_2, request);

            // then
            assertThat(response).isNotNull();
            verify(conversationRepository).save(any(GuildDirectConversation.class));
        }

        @Test
        @DisplayName("자기 자신에게 DM을 보낼 수 없다")
        void sendMessage_toSelf_fail() {
            // given
            DirectMessageRequest request = DirectMessageRequest.builder()
                .content("자신에게")
                .build();

            when(guildRepository.existsByIdAndIsActiveTrue(1L)).thenReturn(true);

            // when & then
            assertThatThrownBy(() -> dmService.sendMessage(1L, USER_ID_1, USER_ID_1, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("자기 자신에게 DM을 보낼 수 없습니다");
        }

        @Test
        @DisplayName("발신자가 길드 멤버가 아니면 실패한다")
        void sendMessage_senderNotMember_fail() {
            // given
            DirectMessageRequest request = DirectMessageRequest.builder()
                .content("안녕하세요!")
                .build();

            when(guildRepository.existsByIdAndIsActiveTrue(1L)).thenReturn(true);
            when(memberRepository.isActiveMember(1L, USER_ID_1)).thenReturn(false);

            // when & then
            assertThatThrownBy(() -> dmService.sendMessage(1L, USER_ID_1, USER_ID_2, request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("발신자가 길드 멤버가 아닙니다");
        }

        @Test
        @DisplayName("수신자가 길드 멤버가 아니면 실패한다")
        void sendMessage_recipientNotMember_fail() {
            // given
            DirectMessageRequest request = DirectMessageRequest.builder()
                .content("안녕하세요!")
                .build();

            when(guildRepository.existsByIdAndIsActiveTrue(1L)).thenReturn(true);
            when(memberRepository.isActiveMember(1L, USER_ID_1)).thenReturn(true);
            when(memberRepository.isActiveMember(1L, USER_ID_2)).thenReturn(false);

            // when & then
            assertThatThrownBy(() -> dmService.sendMessage(1L, USER_ID_1, USER_ID_2, request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("수신자가 길드 멤버가 아닙니다");
        }

        @Test
        @DisplayName("존재하지 않는 길드에 DM 전송 시 예외 발생")
        void sendMessage_guildNotFound_fail() {
            // given
            DirectMessageRequest request = DirectMessageRequest.builder()
                .content("안녕하세요!")
                .build();

            when(guildRepository.existsByIdAndIsActiveTrue(999L)).thenReturn(false);

            // when & then
            assertThatThrownBy(() -> dmService.sendMessage(999L, USER_ID_1, USER_ID_2, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("길드를 찾을 수 없습니다");
        }
    }

    @Nested
    @DisplayName("대화 목록 조회 테스트")
    class GetConversationsTest {

        @Test
        @DisplayName("대화 목록을 조회한다")
        void getConversations_success() {
            // given
            when(memberRepository.isActiveMember(1L, USER_ID_1)).thenReturn(true);
            when(conversationRepository.findAllByGuildIdAndUserId(1L, USER_ID_1))
                .thenReturn(List.of(testConversation));
            when(userProfileCacheService.getUserProfiles(List.of(USER_ID_2)))
                .thenReturn(java.util.Map.of(USER_ID_2, new UserProfileCache(USER_ID_2, NICKNAME_2, null, 1, null, null, null)));
            when(messageRepository.countUnreadMessages(1L, USER_ID_1)).thenReturn(3);

            // when
            List<DirectConversationResponse> result = dmService.getConversations(1L, USER_ID_1);

            // then
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getOtherUserId()).isEqualTo(USER_ID_2);
            assertThat(result.get(0).getOtherUserNickname()).isEqualTo(NICKNAME_2);
            assertThat(result.get(0).getUnreadCount()).isEqualTo(3);
        }

        @Test
        @DisplayName("비멤버는 대화 목록을 조회할 수 없다")
        void getConversations_nonMember_fail() {
            // given
            when(memberRepository.isActiveMember(1L, USER_ID_1)).thenReturn(false);

            // when & then
            assertThatThrownBy(() -> dmService.getConversations(1L, USER_ID_1))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("길드 멤버만 DM을 사용할 수 있습니다");
        }
    }

    @Nested
    @DisplayName("메시지 조회 테스트")
    class GetMessagesTest {

        @Test
        @DisplayName("대화 ID로 메시지 목록을 조회한다")
        void getMessagesByConversationId_success() {
            // given
            Pageable pageable = PageRequest.of(0, 50);
            Page<GuildDirectMessage> messagePage = new PageImpl<>(List.of(testMessage), pageable, 1);

            when(memberRepository.isActiveMember(1L, USER_ID_1)).thenReturn(true);
            when(conversationRepository.findById(1L)).thenReturn(Optional.of(testConversation));
            when(messageRepository.findByConversationId(1L, pageable)).thenReturn(messagePage);

            // when
            Page<DirectMessageResponse> result = dmService.getMessagesByConversationId(
                1L, USER_ID_1, 1L, pageable);

            // then
            assertThat(result).isNotNull();
            assertThat(result.getContent()).hasSize(1);
        }

        @Test
        @DisplayName("대화 참여자가 아니면 메시지를 조회할 수 없다")
        void getMessagesByConversationId_notParticipant_fail() {
            // given
            String otherUserId = "user-ccc";
            Pageable pageable = PageRequest.of(0, 50);

            when(memberRepository.isActiveMember(1L, otherUserId)).thenReturn(true);
            when(conversationRepository.findById(1L)).thenReturn(Optional.of(testConversation));

            // when & then
            assertThatThrownBy(() -> dmService.getMessagesByConversationId(1L, otherUserId, 1L, pageable))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("해당 대화에 접근할 수 없습니다");
        }

        @Test
        @DisplayName("다른 길드의 대화는 조회할 수 없다")
        void getMessagesByConversationId_wrongGuild_fail() {
            // given
            Pageable pageable = PageRequest.of(0, 50);

            when(memberRepository.isActiveMember(2L, USER_ID_1)).thenReturn(true);
            when(conversationRepository.findById(1L)).thenReturn(Optional.of(testConversation));

            // when & then
            assertThatThrownBy(() -> dmService.getMessagesByConversationId(2L, USER_ID_1, 1L, pageable))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("해당 길드의 대화가 아닙니다");
        }
    }

    @Nested
    @DisplayName("읽음 처리 테스트")
    class MarkAsReadTest {

        @Test
        @DisplayName("메시지를 읽음 처리한다")
        void markAsRead_success() {
            // given
            when(memberRepository.isActiveMember(1L, USER_ID_2)).thenReturn(true);
            when(conversationRepository.findById(1L)).thenReturn(Optional.of(testConversation));
            when(messageRepository.markAllAsRead(1L, USER_ID_2)).thenReturn(5);

            // when
            dmService.markAsRead(1L, USER_ID_2, 1L);

            // then
            verify(messageRepository).markAllAsRead(1L, USER_ID_2);
        }
    }

    @Nested
    @DisplayName("안읽은 메시지 수 조회 테스트")
    class GetUnreadCountTest {

        @Test
        @DisplayName("전체 안읽은 DM 수를 조회한다")
        void getTotalUnreadCount_success() {
            // given
            when(memberRepository.isActiveMember(1L, USER_ID_1)).thenReturn(true);
            when(messageRepository.countTotalUnreadMessages(1L, USER_ID_1)).thenReturn(10);

            // when
            int count = dmService.getTotalUnreadCount(1L, USER_ID_1);

            // then
            assertThat(count).isEqualTo(10);
        }
    }

    @Nested
    @DisplayName("대화 생성/조회 테스트")
    class GetOrCreateConversationTest {

        @Test
        @DisplayName("기존 대화를 조회한다")
        void getOrCreateConversation_existing() {
            // given
            when(guildRepository.existsByIdAndIsActiveTrue(1L)).thenReturn(true);
            when(memberRepository.isActiveMember(1L, USER_ID_1)).thenReturn(true);
            when(memberRepository.isActiveMember(1L, USER_ID_2)).thenReturn(true);
            when(conversationRepository.findConversation(1L, USER_ID_1, USER_ID_2))
                .thenReturn(Optional.of(testConversation));
            when(userProfileCacheService.getUserProfile(USER_ID_2)).thenReturn(new UserProfileCache(USER_ID_2, NICKNAME_2, null, 1, null, null, null));
            when(messageRepository.countUnreadMessages(1L, USER_ID_1)).thenReturn(0);

            // when
            DirectConversationResponse result = dmService.getOrCreateConversation(1L, USER_ID_1, USER_ID_2);

            // then
            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(1L);
            assertThat(result.getOtherUserNickname()).isEqualTo(NICKNAME_2);
        }

        @Test
        @DisplayName("새 대화를 생성한다")
        void getOrCreateConversation_new() {
            // given
            when(guildRepository.existsByIdAndIsActiveTrue(1L)).thenReturn(true);
            when(memberRepository.isActiveMember(1L, USER_ID_1)).thenReturn(true);
            when(memberRepository.isActiveMember(1L, USER_ID_2)).thenReturn(true);
            when(conversationRepository.findConversation(1L, USER_ID_1, USER_ID_2))
                .thenReturn(Optional.empty());
            when(conversationRepository.save(any(GuildDirectConversation.class))).thenAnswer(inv -> {
                GuildDirectConversation conv = inv.getArgument(0);
                setId(conv, GuildDirectConversation.class, 2L);
                return conv;
            });
            when(userProfileCacheService.getUserProfile(USER_ID_2)).thenReturn(new UserProfileCache(USER_ID_2, NICKNAME_2, null, 1, null, null, null));
            when(messageRepository.countUnreadMessages(anyLong(), anyString())).thenReturn(0);

            // when
            DirectConversationResponse result = dmService.getOrCreateConversation(1L, USER_ID_1, USER_ID_2);

            // then
            assertThat(result).isNotNull();
            verify(conversationRepository).save(any(GuildDirectConversation.class));
        }
    }

    @Nested
    @DisplayName("메시지 삭제 테스트")
    class DeleteMessageTest {

        @Test
        @DisplayName("본인 메시지를 삭제한다")
        void deleteMessage_success() {
            // given
            when(memberRepository.isActiveMember(1L, USER_ID_1)).thenReturn(true);
            when(messageRepository.findById(1L)).thenReturn(Optional.of(testMessage));

            // when
            dmService.deleteMessage(1L, USER_ID_1, 1L);

            // then
            assertThat(testMessage.getIsDeleted()).isTrue();
        }

        @Test
        @DisplayName("다른 사람 메시지는 삭제할 수 없다")
        void deleteMessage_notOwner_fail() {
            // given
            when(memberRepository.isActiveMember(1L, USER_ID_2)).thenReturn(true);
            when(messageRepository.findById(1L)).thenReturn(Optional.of(testMessage));

            // when & then
            assertThatThrownBy(() -> dmService.deleteMessage(1L, USER_ID_2, 1L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("본인 메시지만 삭제할 수 있습니다");
        }

        @Test
        @DisplayName("존재하지 않는 메시지 삭제 시 예외 발생")
        void deleteMessage_notFound_fail() {
            // given
            when(memberRepository.isActiveMember(1L, USER_ID_1)).thenReturn(true);
            when(messageRepository.findById(999L)).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> dmService.deleteMessage(1L, USER_ID_1, 999L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("메시지를 찾을 수 없습니다");
        }
    }
}
