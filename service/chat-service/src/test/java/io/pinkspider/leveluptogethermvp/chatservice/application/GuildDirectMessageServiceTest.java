package io.pinkspider.leveluptogethermvp.chatservice.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.pinkspider.leveluptogethermvp.chatservice.domain.dto.DirectConversationResponse;
import io.pinkspider.leveluptogethermvp.chatservice.domain.dto.DirectMessageRequest;
import io.pinkspider.leveluptogethermvp.chatservice.domain.dto.DirectMessageResponse;
import io.pinkspider.leveluptogethermvp.chatservice.domain.entity.GuildDirectConversation;
import io.pinkspider.leveluptogethermvp.chatservice.domain.entity.GuildDirectMessage;
import io.pinkspider.leveluptogethermvp.chatservice.infrastructure.GuildDirectConversationRepository;
import io.pinkspider.leveluptogethermvp.chatservice.infrastructure.GuildDirectMessageRepository;
import io.pinkspider.leveluptogethermvp.chatservice.realtime.DmRealtimePublisher;
import io.pinkspider.global.event.GuildDirectMessageEvent;
import io.pinkspider.global.facade.GuildQueryFacade;
import io.pinkspider.global.facade.UserQueryFacade;
import io.pinkspider.global.facade.dto.UserProfileInfo;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
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
    private GuildQueryFacade guildQueryFacadeService;

    @Mock
    private UserQueryFacade userQueryFacadeService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private DmPresenceService dmPresenceService;

    @Mock
    private DmRealtimePublisher dmRealtimePublisher;

    @Mock
    private io.pinkspider.global.facade.GamificationQueryFacade gamificationQueryFacadeService;

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

            when(guildQueryFacadeService.guildExists(1L)).thenReturn(true);
            when(guildQueryFacadeService.isActiveMember(1L, USER_ID_1)).thenReturn(true);
            when(guildQueryFacadeService.isActiveMember(1L, USER_ID_2)).thenReturn(true);
            when(userQueryFacadeService.getActiveUserIds(List.of(USER_ID_2)))
                .thenReturn(List.of(USER_ID_2));
            when(userQueryFacadeService.getUserNickname(USER_ID_1)).thenReturn(NICKNAME_1);
            when(conversationRepository.findConversationIncludingInactive(1L, USER_ID_1, USER_ID_2))
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
        @DisplayName("LUT-383: 발신자가 차단한 상대에게는 DM 전송이 에러로 막힌다")
        void sendMessage_senderBlocking_throws() {
            // given
            DirectMessageRequest request = DirectMessageRequest.builder()
                .content("안녕하세요!")
                .build();

            when(guildQueryFacadeService.guildExists(1L)).thenReturn(true);
            when(guildQueryFacadeService.isActiveMember(1L, USER_ID_1)).thenReturn(true);
            when(guildQueryFacadeService.isActiveMember(1L, USER_ID_2)).thenReturn(true);
            when(userQueryFacadeService.getActiveUserIds(List.of(USER_ID_2)))
                .thenReturn(List.of(USER_ID_2));
            when(userQueryFacadeService.getBlockedUserIds(USER_ID_1))
                .thenReturn(List.of(USER_ID_2));

            // when & then
            org.assertj.core.api.Assertions.assertThatThrownBy(
                    () -> dmService.sendMessage(1L, USER_ID_1, USER_ID_2, request))
                .isInstanceOf(io.pinkspider.global.exception.CustomException.class)
                .hasMessageContaining("error.dm.blocked_user");
            verify(messageRepository, org.mockito.Mockito.never()).save(any(GuildDirectMessage.class));
        }

        @Test
        @DisplayName("LUT-383: 피차단자 발신은 정상 저장되지만 수신자에게는 어디에도 전달되지 않는다 (shadow block)")
        void sendMessage_shadowBlocked_savedButHiddenFromRecipient() {
            // given: USER_ID_2(수신자)가 USER_ID_1(발신자)을 차단한 상태
            DirectMessageRequest request = DirectMessageRequest.builder()
                .content("조용한 차단 테스트")
                .build();

            when(guildQueryFacadeService.guildExists(1L)).thenReturn(true);
            when(guildQueryFacadeService.isActiveMember(1L, USER_ID_1)).thenReturn(true);
            when(guildQueryFacadeService.isActiveMember(1L, USER_ID_2)).thenReturn(true);
            when(userQueryFacadeService.getActiveUserIds(List.of(USER_ID_2)))
                .thenReturn(List.of(USER_ID_2));
            when(userQueryFacadeService.getBlockedUserIds(USER_ID_1)).thenReturn(List.of());
            when(userQueryFacadeService.getBlockedUserIds(USER_ID_2))
                .thenReturn(List.of(USER_ID_1));
            when(userQueryFacadeService.getUserNickname(USER_ID_1)).thenReturn(NICKNAME_1);
            when(conversationRepository.findConversationIncludingInactive(1L, USER_ID_1, USER_ID_2))
                .thenReturn(Optional.of(testConversation));
            when(messageRepository.save(any(GuildDirectMessage.class))).thenAnswer(inv -> {
                GuildDirectMessage msg = inv.getArgument(0);
                setId(msg, GuildDirectMessage.class, 1L);
                return msg;
            });

            // when
            DirectMessageResponse response = dmService.sendMessage(1L, USER_ID_1, USER_ID_2, request);

            // then: 발신자 화면은 정상 전송(저장 + 에코), 수신자 실시간·알림 이벤트는 생략
            assertThat(response).isNotNull();
            verify(messageRepository).save(any(GuildDirectMessage.class));
            verify(dmRealtimePublisher).publishToUser(
                eq(USER_ID_1), eq(GuildDirectMessageService.DM_DESTINATION), any());
            verify(dmRealtimePublisher, never()).publishToUser(
                eq(USER_ID_2), eq(GuildDirectMessageService.DM_DESTINATION), any());
            verify(eventPublisher, never()).publishEvent(any(GuildDirectMessageEvent.class));
        }

        @Test
        @DisplayName("DM 전송 시 알림 이벤트를 발행한다 (LUT-224)")
        void sendMessage_publishesDirectMessageEvent() {
            // given
            DirectMessageRequest request = DirectMessageRequest.builder()
                .content("알림 이벤트 테스트")
                .build();

            when(guildQueryFacadeService.guildExists(1L)).thenReturn(true);
            when(guildQueryFacadeService.isActiveMember(1L, USER_ID_1)).thenReturn(true);
            when(guildQueryFacadeService.isActiveMember(1L, USER_ID_2)).thenReturn(true);
            when(userQueryFacadeService.getActiveUserIds(List.of(USER_ID_2)))
                .thenReturn(List.of(USER_ID_2));
            when(userQueryFacadeService.getUserNickname(USER_ID_1)).thenReturn(NICKNAME_1);
            when(conversationRepository.findConversationIncludingInactive(1L, USER_ID_1, USER_ID_2))
                .thenReturn(Optional.of(testConversation));
            when(messageRepository.save(any(GuildDirectMessage.class))).thenAnswer(inv -> {
                GuildDirectMessage msg = inv.getArgument(0);
                setId(msg, GuildDirectMessage.class, 77L);
                return msg;
            });

            // when
            dmService.sendMessage(1L, USER_ID_1, USER_ID_2, request);

            // then
            ArgumentCaptor<GuildDirectMessageEvent> captor =
                ArgumentCaptor.forClass(GuildDirectMessageEvent.class);
            verify(eventPublisher).publishEvent(captor.capture());
            GuildDirectMessageEvent event = captor.getValue();
            assertThat(event.userId()).isEqualTo(USER_ID_1);
            assertThat(event.senderNickname()).isEqualTo(NICKNAME_1);
            assertThat(event.guildId()).isEqualTo(1L);
            assertThat(event.conversationId()).isEqualTo(1L);
            assertThat(event.messageId()).isEqualTo(77L);
            assertThat(event.messageContent()).isEqualTo("알림 이벤트 테스트");
            assertThat(event.recipientId()).isEqualTo(USER_ID_2);
        }

        @Test
        @DisplayName("수신자가 대화방을 보고 있으면 알림 이벤트를 발행하지 않는다 (LUT-263)")
        void sendMessage_recipientViewing_skipsNotificationEvent() {
            // given
            DirectMessageRequest request = DirectMessageRequest.builder()
                .content("보고 있는 중")
                .build();

            when(guildQueryFacadeService.guildExists(1L)).thenReturn(true);
            when(guildQueryFacadeService.isActiveMember(1L, USER_ID_1)).thenReturn(true);
            when(guildQueryFacadeService.isActiveMember(1L, USER_ID_2)).thenReturn(true);
            when(userQueryFacadeService.getActiveUserIds(List.of(USER_ID_2)))
                .thenReturn(List.of(USER_ID_2));
            when(userQueryFacadeService.getUserNickname(USER_ID_1)).thenReturn(NICKNAME_1);
            when(conversationRepository.findConversationIncludingInactive(1L, USER_ID_1, USER_ID_2))
                .thenReturn(Optional.of(testConversation));
            when(messageRepository.save(any(GuildDirectMessage.class))).thenAnswer(inv -> {
                GuildDirectMessage msg = inv.getArgument(0);
                setId(msg, GuildDirectMessage.class, 1L);
                return msg;
            });
            when(dmPresenceService.isViewing(USER_ID_2, 1L)).thenReturn(true);

            // when
            dmService.sendMessage(1L, USER_ID_1, USER_ID_2, request);

            // then: 알림(레코드+푸시) 이벤트는 생략, 실시간 전달은 정상 수행
            verify(eventPublisher, never()).publishEvent(any(GuildDirectMessageEvent.class));
            verify(dmRealtimePublisher).publishToUser(
                eq(USER_ID_2), eq(GuildDirectMessageService.DM_DESTINATION), any());
            verify(dmRealtimePublisher).publishToUser(
                eq(USER_ID_1), eq(GuildDirectMessageService.DM_DESTINATION), any());
        }

        @Test
        @DisplayName("DM 전송 시 수신자와 발신자(에코)에게 실시간 발행한다 (LUT-263)")
        void sendMessage_publishesRealtimeToBothUsers() {
            // given
            DirectMessageRequest request = DirectMessageRequest.builder()
                .content("실시간 전달")
                .build();

            when(guildQueryFacadeService.guildExists(1L)).thenReturn(true);
            when(guildQueryFacadeService.isActiveMember(1L, USER_ID_1)).thenReturn(true);
            when(guildQueryFacadeService.isActiveMember(1L, USER_ID_2)).thenReturn(true);
            when(userQueryFacadeService.getActiveUserIds(List.of(USER_ID_2)))
                .thenReturn(List.of(USER_ID_2));
            when(userQueryFacadeService.getUserNickname(USER_ID_1)).thenReturn(NICKNAME_1);
            when(conversationRepository.findConversationIncludingInactive(1L, USER_ID_1, USER_ID_2))
                .thenReturn(Optional.of(testConversation));
            when(messageRepository.save(any(GuildDirectMessage.class))).thenAnswer(inv -> {
                GuildDirectMessage msg = inv.getArgument(0);
                setId(msg, GuildDirectMessage.class, 1L);
                return msg;
            });

            // when
            dmService.sendMessage(1L, USER_ID_1, USER_ID_2, request);

            // then
            ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
            verify(dmRealtimePublisher).publishToUser(
                eq(USER_ID_2), eq(GuildDirectMessageService.DM_DESTINATION), payloadCaptor.capture());
            verify(dmRealtimePublisher).publishToUser(
                eq(USER_ID_1), eq(GuildDirectMessageService.DM_DESTINATION), any());
            assertThat(payloadCaptor.getValue()).isInstanceOf(DirectMessageResponse.class);
            // 보고 있지 않으므로 알림 이벤트도 발행
            verify(eventPublisher).publishEvent(any(GuildDirectMessageEvent.class));
        }

        @Test
        @DisplayName("이미지 DM을 전송한다")
        void sendMessage_image_success() {
            // given
            DirectMessageRequest request = DirectMessageRequest.builder()
                .content("이미지입니다")
                .imageUrl("https://example.com/image.jpg")
                .build();

            when(guildQueryFacadeService.guildExists(1L)).thenReturn(true);
            when(guildQueryFacadeService.isActiveMember(1L, USER_ID_1)).thenReturn(true);
            when(guildQueryFacadeService.isActiveMember(1L, USER_ID_2)).thenReturn(true);
            when(userQueryFacadeService.getActiveUserIds(List.of(USER_ID_2)))
                .thenReturn(List.of(USER_ID_2));
            when(userQueryFacadeService.getUserNickname(USER_ID_1)).thenReturn(NICKNAME_1);
            when(conversationRepository.findConversationIncludingInactive(1L, USER_ID_1, USER_ID_2))
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

            when(guildQueryFacadeService.guildExists(1L)).thenReturn(true);
            when(guildQueryFacadeService.isActiveMember(1L, USER_ID_1)).thenReturn(true);
            when(guildQueryFacadeService.isActiveMember(1L, USER_ID_2)).thenReturn(true);
            when(userQueryFacadeService.getActiveUserIds(List.of(USER_ID_2)))
                .thenReturn(List.of(USER_ID_2));
            when(userQueryFacadeService.getUserNickname(USER_ID_1)).thenReturn(NICKNAME_1);
            when(conversationRepository.findConversationIncludingInactive(1L, USER_ID_1, USER_ID_2))
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

            when(guildQueryFacadeService.guildExists(1L)).thenReturn(true);

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

            when(guildQueryFacadeService.guildExists(1L)).thenReturn(true);
            when(guildQueryFacadeService.isActiveMember(1L, USER_ID_1)).thenReturn(false);

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

            when(guildQueryFacadeService.guildExists(1L)).thenReturn(true);
            when(guildQueryFacadeService.isActiveMember(1L, USER_ID_1)).thenReturn(true);
            when(guildQueryFacadeService.isActiveMember(1L, USER_ID_2)).thenReturn(false);

            // when & then
            assertThatThrownBy(() -> dmService.sendMessage(1L, USER_ID_1, USER_ID_2, request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("수신자가 길드 멤버가 아닙니다");
        }

        @Test
        @DisplayName("비활성화된 대화방에 메시지를 보내면 재활성화된다 (LUT-287 재가입)")
        void sendMessage_reactivatesInactiveConversation() {
            // given
            DirectMessageRequest request = DirectMessageRequest.builder()
                .content("재가입 후 첫 메시지")
                .build();
            testConversation.deactivate();

            when(guildQueryFacadeService.guildExists(1L)).thenReturn(true);
            when(guildQueryFacadeService.isActiveMember(1L, USER_ID_1)).thenReturn(true);
            when(guildQueryFacadeService.isActiveMember(1L, USER_ID_2)).thenReturn(true);
            when(userQueryFacadeService.getActiveUserIds(List.of(USER_ID_2)))
                .thenReturn(List.of(USER_ID_2));
            when(userQueryFacadeService.getUserNickname(USER_ID_1)).thenReturn(NICKNAME_1);
            when(conversationRepository.findConversationIncludingInactive(1L, USER_ID_1, USER_ID_2))
                .thenReturn(Optional.of(testConversation));
            when(messageRepository.save(any(GuildDirectMessage.class))).thenAnswer(inv -> {
                GuildDirectMessage msg = inv.getArgument(0);
                setId(msg, GuildDirectMessage.class, 1L);
                return msg;
            });

            // when
            dmService.sendMessage(1L, USER_ID_1, USER_ID_2, request);

            // then
            assertThat(testConversation.getIsActive()).isTrue();
        }

        @Test
        @DisplayName("회원 탈퇴한 유저에게는 DM을 보낼 수 없다 (LUT-285)")
        void sendMessage_recipientWithdrawn_fail() {
            // given
            DirectMessageRequest request = DirectMessageRequest.builder()
                .content("안녕하세요!")
                .build();

            when(guildQueryFacadeService.guildExists(1L)).thenReturn(true);
            when(guildQueryFacadeService.isActiveMember(1L, USER_ID_1)).thenReturn(true);
            // 회원 탈퇴자는 길드 멤버십이 잔존해(LUT-287) 멤버 검사를 통과한다
            when(guildQueryFacadeService.isActiveMember(1L, USER_ID_2)).thenReturn(true);
            when(userQueryFacadeService.getActiveUserIds(List.of(USER_ID_2))).thenReturn(List.of());

            // when & then
            assertThatThrownBy(() -> dmService.sendMessage(1L, USER_ID_1, USER_ID_2, request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("탈퇴한 회원에게는 DM을 보낼 수 없습니다");
            verify(messageRepository, never()).save(any());
        }

        @Test
        @DisplayName("존재하지 않는 길드에 DM 전송 시 예외 발생")
        void sendMessage_guildNotFound_fail() {
            // given
            DirectMessageRequest request = DirectMessageRequest.builder()
                .content("안녕하세요!")
                .build();

            when(guildQueryFacadeService.guildExists(999L)).thenReturn(false);

            // when & then
            assertThatThrownBy(() -> dmService.sendMessage(999L, USER_ID_1, USER_ID_2, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("길드를 찾을 수 없습니다");
        }
    }

    private static io.pinkspider.global.facade.dto.UserTitleDto equippedTitle(
            String name,
            io.pinkspider.global.enums.TitleRarity rarity,
            io.pinkspider.global.enums.TitlePosition position) {
        return new io.pinkspider.global.facade.dto.UserTitleDto(
            1L, USER_ID_2, 1L, name, null, null, null,
            null, null, null, null, rarity, position, null, null, true, position, null);
    }

    @Nested
    @DisplayName("대화 목록 조회 테스트")
    class GetConversationsTest {

        @Test
        @DisplayName("대화 목록을 조회한다")
        void getConversations_success() {
            // given
            when(guildQueryFacadeService.isActiveMember(1L, USER_ID_1)).thenReturn(true);
            when(conversationRepository.findAllByGuildIdAndUserId(1L, USER_ID_1))
                .thenReturn(List.of(testConversation));
            when(guildQueryFacadeService.getActiveMemberUserIds(1L))
                .thenReturn(List.of(USER_ID_1, USER_ID_2));
            when(userQueryFacadeService.getActiveUserIds(List.of(USER_ID_2)))
                .thenReturn(List.of(USER_ID_2));
            when(userQueryFacadeService.getUserProfiles(List.of(USER_ID_2)))
                .thenReturn(java.util.Map.of(USER_ID_2, new UserProfileInfo(USER_ID_2, NICKNAME_2, null, 1, null, null, null)));
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
        @DisplayName("[LUT-443] 상대방 칭호·장착 아이템 등급을 응답에 채운다")
        void getConversations_enrichesOtherUserRarities() {
            // given
            when(guildQueryFacadeService.isActiveMember(1L, USER_ID_1)).thenReturn(true);
            when(conversationRepository.findAllByGuildIdAndUserId(1L, USER_ID_1))
                .thenReturn(List.of(testConversation));
            when(guildQueryFacadeService.getActiveMemberUserIds(1L))
                .thenReturn(List.of(USER_ID_1, USER_ID_2));
            when(userQueryFacadeService.getActiveUserIds(List.of(USER_ID_2)))
                .thenReturn(List.of(USER_ID_2));
            when(userQueryFacadeService.getUserProfiles(List.of(USER_ID_2)))
                .thenReturn(java.util.Map.of(USER_ID_2, new UserProfileInfo(USER_ID_2, NICKNAME_2, null, 1, null, null, null)));
            when(messageRepository.countUnreadMessages(1L, USER_ID_1)).thenReturn(0);

            when(gamificationQueryFacadeService.getEquippedTitlesByUserIds(List.of(USER_ID_2)))
                .thenReturn(java.util.Map.of(USER_ID_2, List.of(
                    equippedTitle("용사", io.pinkspider.global.enums.TitleRarity.EPIC,
                        io.pinkspider.global.enums.TitlePosition.LEFT),
                    equippedTitle("정복자", io.pinkspider.global.enums.TitleRarity.RARE,
                        io.pinkspider.global.enums.TitlePosition.RIGHT))));
            when(gamificationQueryFacadeService.getEquippedItemRaritiesByUserIds(List.of(USER_ID_2)))
                .thenReturn(java.util.Map.of(USER_ID_2, List.of(
                    new io.pinkspider.global.facade.dto.EquippedItemRarityDto(
                        "HEAD", io.pinkspider.global.enums.TitleRarity.LEGENDARY))));

            // when
            List<DirectConversationResponse> result = dmService.getConversations(1L, USER_ID_1);

            // then
            assertThat(result).hasSize(1);
            DirectConversationResponse response = result.get(0);
            assertThat(response.getOtherUserLeftTitleRarity())
                .isEqualTo(io.pinkspider.global.enums.TitleRarity.EPIC);
            assertThat(response.getOtherUserRightTitleRarity())
                .isEqualTo(io.pinkspider.global.enums.TitleRarity.RARE);
            assertThat(response.getOtherUserEquippedItemRarities()).hasSize(1);
            assertThat(response.getOtherUserEquippedItemRarities().get(0).itemType())
                .isEqualTo("HEAD");
        }

        @Test
        @DisplayName("[LUT-443] 등급 조회가 실패해도 목록은 빈 등급으로 내려간다")
        void getConversations_rarityLookupFailure_fallsBackToEmpty() {
            // given
            when(guildQueryFacadeService.isActiveMember(1L, USER_ID_1)).thenReturn(true);
            when(conversationRepository.findAllByGuildIdAndUserId(1L, USER_ID_1))
                .thenReturn(List.of(testConversation));
            when(guildQueryFacadeService.getActiveMemberUserIds(1L))
                .thenReturn(List.of(USER_ID_1, USER_ID_2));
            when(userQueryFacadeService.getActiveUserIds(List.of(USER_ID_2)))
                .thenReturn(List.of(USER_ID_2));
            when(userQueryFacadeService.getUserProfiles(List.of(USER_ID_2)))
                .thenReturn(java.util.Map.of(USER_ID_2, new UserProfileInfo(USER_ID_2, NICKNAME_2, null, 1, null, null, null)));
            when(messageRepository.countUnreadMessages(1L, USER_ID_1)).thenReturn(0);

            when(gamificationQueryFacadeService.getEquippedTitlesByUserIds(List.of(USER_ID_2)))
                .thenThrow(new RuntimeException("gamification down"));
            when(gamificationQueryFacadeService.getEquippedItemRaritiesByUserIds(List.of(USER_ID_2)))
                .thenThrow(new RuntimeException("gamification down"));

            // when
            List<DirectConversationResponse> result = dmService.getConversations(1L, USER_ID_1);

            // then
            assertThat(result).hasSize(1);
            DirectConversationResponse response = result.get(0);
            assertThat(response.getOtherUserLeftTitleRarity()).isNull();
            assertThat(response.getOtherUserRightTitleRarity()).isNull();
            assertThat(response.getOtherUserEquippedItemRarities()).isEmpty();
        }

        @Test
        @DisplayName("길드를 탈퇴한 상대와의 대화는 목록에서 제외한다 (LUT-285)")
        void getConversations_excludesGuildLeftUser() {
            // given
            when(guildQueryFacadeService.isActiveMember(1L, USER_ID_1)).thenReturn(true);
            when(conversationRepository.findAllByGuildIdAndUserId(1L, USER_ID_1))
                .thenReturn(List.of(testConversation));
            // 상대(USER_ID_2)가 길드 활성 멤버 목록에 없음 (길드 탈퇴)
            when(guildQueryFacadeService.getActiveMemberUserIds(1L)).thenReturn(List.of(USER_ID_1));
            when(userQueryFacadeService.getActiveUserIds(List.of(USER_ID_2)))
                .thenReturn(List.of(USER_ID_2));

            // when
            List<DirectConversationResponse> result = dmService.getConversations(1L, USER_ID_1);

            // then
            assertThat(result).isEmpty();
            verify(messageRepository, never()).countUnreadMessages(anyLong(), anyString());
        }

        @Test
        @DisplayName("LUT-383: 내가 차단한 상대와의 대화는 목록에서 숨긴다")
        void getConversations_excludesBlockedUser() {
            // given
            when(guildQueryFacadeService.isActiveMember(1L, USER_ID_1)).thenReturn(true);
            when(conversationRepository.findAllByGuildIdAndUserId(1L, USER_ID_1))
                .thenReturn(List.of(testConversation));
            when(guildQueryFacadeService.getActiveMemberUserIds(1L))
                .thenReturn(List.of(USER_ID_1, USER_ID_2));
            when(userQueryFacadeService.getActiveUserIds(List.of(USER_ID_2)))
                .thenReturn(List.of(USER_ID_2));
            when(userQueryFacadeService.getBlockedUserIds(USER_ID_1))
                .thenReturn(List.of(USER_ID_2));

            // when
            List<DirectConversationResponse> result = dmService.getConversations(1L, USER_ID_1);

            // then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("회원 탈퇴한 상대와의 대화는 목록에서 제외한다 (LUT-285)")
        void getConversations_excludesWithdrawnUser() {
            // given
            when(guildQueryFacadeService.isActiveMember(1L, USER_ID_1)).thenReturn(true);
            when(conversationRepository.findAllByGuildIdAndUserId(1L, USER_ID_1))
                .thenReturn(List.of(testConversation));
            // 회원 탈퇴자는 길드 멤버십이 정리되지 않아(LUT-287) 멤버 목록에는 남아 있다
            when(guildQueryFacadeService.getActiveMemberUserIds(1L))
                .thenReturn(List.of(USER_ID_1, USER_ID_2));
            when(userQueryFacadeService.getActiveUserIds(List.of(USER_ID_2))).thenReturn(List.of());

            // when
            List<DirectConversationResponse> result = dmService.getConversations(1L, USER_ID_1);

            // then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("비멤버는 대화 목록을 조회할 수 없다")
        void getConversations_nonMember_fail() {
            // given
            when(guildQueryFacadeService.isActiveMember(1L, USER_ID_1)).thenReturn(false);

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

            when(guildQueryFacadeService.isActiveMember(1L, USER_ID_1)).thenReturn(true);
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

            when(guildQueryFacadeService.isActiveMember(1L, otherUserId)).thenReturn(true);
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

            when(guildQueryFacadeService.isActiveMember(2L, USER_ID_1)).thenReturn(true);
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
            when(guildQueryFacadeService.isActiveMember(1L, USER_ID_2)).thenReturn(true);
            when(conversationRepository.findById(1L)).thenReturn(Optional.of(testConversation));
            when(messageRepository.markAllAsRead(1L, USER_ID_2)).thenReturn(5);

            // when
            dmService.markAsRead(1L, USER_ID_2, 1L);

            // then
            verify(messageRepository).markAllAsRead(1L, USER_ID_2);
            // LUT-263: 읽음 처리는 방을 보고 있다는 신호 → presence 갱신
            verify(dmPresenceService).markViewing(USER_ID_2, 1L);
        }

        @Test
        @DisplayName("LUT-383: 차단한 상대의 방은 읽음 처리하지 않는다 (피차단자 '안읽음' 유지)")
        void markAsRead_blockedOther_skips() {
            // given: USER_ID_2가 상대(USER_ID_1)를 차단한 상태
            when(guildQueryFacadeService.isActiveMember(1L, USER_ID_2)).thenReturn(true);
            when(conversationRepository.findById(1L)).thenReturn(Optional.of(testConversation));
            when(userQueryFacadeService.getBlockedUserIds(USER_ID_2))
                .thenReturn(List.of(USER_ID_1));

            // when
            dmService.markAsRead(1L, USER_ID_2, 1L);

            // then: 읽음 처리·presence 갱신 모두 생략
            verify(messageRepository, never()).markAllAsRead(anyLong(), anyString());
            verify(dmPresenceService, never()).markViewing(anyString(), anyLong());
        }
    }

    @Nested
    @DisplayName("안읽은 메시지 수 조회 테스트")
    class GetUnreadCountTest {

        @Test
        @DisplayName("전체 안읽은 DM 수를 조회한다 (차단 없음 — __none__ 센티널)")
        void getTotalUnreadCount_success() {
            // given
            when(guildQueryFacadeService.isActiveMember(1L, USER_ID_1)).thenReturn(true);
            when(messageRepository.countTotalUnreadMessages(1L, USER_ID_1, List.of("__none__")))
                .thenReturn(10);

            // when
            int count = dmService.getTotalUnreadCount(1L, USER_ID_1);

            // then
            assertThat(count).isEqualTo(10);
        }

        @Test
        @DisplayName("LUT-383: 차단한 상대의 미읽음은 뱃지 카운트에서 제외된다")
        void getTotalUnreadCount_excludesBlockedSenders() {
            // given
            when(guildQueryFacadeService.isActiveMember(1L, USER_ID_1)).thenReturn(true);
            when(userQueryFacadeService.getBlockedUserIds(USER_ID_1))
                .thenReturn(List.of(USER_ID_2));
            when(messageRepository.countTotalUnreadMessages(1L, USER_ID_1, List.of(USER_ID_2)))
                .thenReturn(3);

            // when
            int count = dmService.getTotalUnreadCount(1L, USER_ID_1);

            // then
            assertThat(count).isEqualTo(3);
        }
    }

    @Nested
    @DisplayName("대화 생성/조회 테스트")
    class GetOrCreateConversationTest {

        @Test
        @DisplayName("기존 대화를 조회한다")
        void getOrCreateConversation_existing() {
            // given
            when(guildQueryFacadeService.guildExists(1L)).thenReturn(true);
            when(guildQueryFacadeService.isActiveMember(1L, USER_ID_1)).thenReturn(true);
            when(guildQueryFacadeService.isActiveMember(1L, USER_ID_2)).thenReturn(true);
            when(userQueryFacadeService.getActiveUserIds(List.of(USER_ID_2)))
                .thenReturn(List.of(USER_ID_2));
            when(conversationRepository.findConversationIncludingInactive(1L, USER_ID_1, USER_ID_2))
                .thenReturn(Optional.of(testConversation));
            when(userQueryFacadeService.getUserProfile(USER_ID_2)).thenReturn(new UserProfileInfo(USER_ID_2, NICKNAME_2, null, 1, null, null, null));
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
            when(guildQueryFacadeService.guildExists(1L)).thenReturn(true);
            when(guildQueryFacadeService.isActiveMember(1L, USER_ID_1)).thenReturn(true);
            when(guildQueryFacadeService.isActiveMember(1L, USER_ID_2)).thenReturn(true);
            when(userQueryFacadeService.getActiveUserIds(List.of(USER_ID_2)))
                .thenReturn(List.of(USER_ID_2));
            when(conversationRepository.findConversationIncludingInactive(1L, USER_ID_1, USER_ID_2))
                .thenReturn(Optional.empty());
            when(conversationRepository.save(any(GuildDirectConversation.class))).thenAnswer(inv -> {
                GuildDirectConversation conv = inv.getArgument(0);
                setId(conv, GuildDirectConversation.class, 2L);
                return conv;
            });
            when(userQueryFacadeService.getUserProfile(USER_ID_2)).thenReturn(new UserProfileInfo(USER_ID_2, NICKNAME_2, null, 1, null, null, null));
            when(messageRepository.countUnreadMessages(anyLong(), anyString())).thenReturn(0);

            // when
            DirectConversationResponse result = dmService.getOrCreateConversation(1L, USER_ID_1, USER_ID_2);

            // then
            assertThat(result).isNotNull();
            verify(conversationRepository).save(any(GuildDirectConversation.class));
        }
    }

    @Nested
    @DisplayName("DM 대화방 비활성화 테스트 (LUT-287)")
    class DeactivateConversationsTest {

        @Test
        @DisplayName("회원 탈퇴 시 유저의 모든 활성 대화방을 비활성화한다")
        void deactivateConversationsForUser_success() {
            // given
            when(conversationRepository.findAllActiveByUserId(USER_ID_1))
                .thenReturn(List.of(testConversation));

            // when
            int count = dmService.deactivateConversationsForUser(USER_ID_1);

            // then
            assertThat(count).isEqualTo(1);
            assertThat(testConversation.getIsActive()).isFalse();
        }

        @Test
        @DisplayName("길드 탈퇴/추방 시 해당 길드의 대화방을 비활성화한다")
        void deactivateConversations_success() {
            // given
            when(conversationRepository.findAllByGuildIdAndUserId(1L, USER_ID_1))
                .thenReturn(List.of(testConversation));

            // when
            int count = dmService.deactivateConversations(1L, USER_ID_1);

            // then
            assertThat(count).isEqualTo(1);
            assertThat(testConversation.getIsActive()).isFalse();
        }
    }

    @Nested
    @DisplayName("메시지 삭제 테스트")
    class DeleteMessageTest {

        @Test
        @DisplayName("본인 메시지를 삭제한다")
        void deleteMessage_success() {
            // given
            when(guildQueryFacadeService.isActiveMember(1L, USER_ID_1)).thenReturn(true);
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
            when(guildQueryFacadeService.isActiveMember(1L, USER_ID_2)).thenReturn(true);
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
            when(guildQueryFacadeService.isActiveMember(1L, USER_ID_1)).thenReturn(true);
            when(messageRepository.findById(999L)).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> dmService.deleteMessage(1L, USER_ID_1, 999L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("메시지를 찾을 수 없습니다");
        }
    }
}
