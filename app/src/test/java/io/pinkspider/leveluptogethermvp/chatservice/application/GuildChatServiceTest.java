package io.pinkspider.leveluptogethermvp.chatservice.application;

import static io.pinkspider.global.test.TestReflectionUtils.setId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.pinkspider.leveluptogethermvp.chatservice.domain.dto.ChatMessageRequest;
import io.pinkspider.leveluptogethermvp.chatservice.domain.dto.ChatMessageResponse;
import io.pinkspider.leveluptogethermvp.chatservice.domain.dto.ChatParticipantResponse;
import io.pinkspider.leveluptogethermvp.chatservice.domain.entity.GuildChatMessage;
import io.pinkspider.leveluptogethermvp.chatservice.domain.entity.GuildChatParticipant;
import io.pinkspider.leveluptogethermvp.chatservice.domain.entity.GuildChatReadStatus;
import io.pinkspider.leveluptogethermvp.chatservice.domain.enums.ChatMessageType;
import io.pinkspider.leveluptogethermvp.chatservice.infrastructure.GuildChatMessageRepository;
import io.pinkspider.leveluptogethermvp.chatservice.infrastructure.GuildChatParticipantRepository;
import io.pinkspider.leveluptogethermvp.chatservice.infrastructure.GuildChatReadStatusRepository;
import io.pinkspider.leveluptogethermvp.guildservice.application.GuildQueryFacadeService;
import io.pinkspider.leveluptogethermvp.guildservice.domain.dto.GuildFacadeDto.GuildBasicInfo;
import io.pinkspider.leveluptogethermvp.userservice.profile.application.UserProfileCacheService;
import io.pinkspider.leveluptogethermvp.userservice.profile.domain.dto.UserProfileCache;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
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
class GuildChatServiceTest {

    @Mock
    private GuildChatMessageRepository chatMessageRepository;

    @Mock
    private GuildChatReadStatusRepository readStatusRepository;

    @Mock
    private GuildChatParticipantRepository participantRepository;

    @Mock
    private GuildQueryFacadeService guildQueryFacadeService;

    @Mock
    private UserProfileCacheService userProfileCacheService;

    @Mock
    private org.springframework.context.ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private GuildChatService guildChatService;

    private GuildChatMessage testMessage;
    private String testUserId;
    private String testNickname;

    @BeforeEach
    void setUp() {
        testUserId = "test-user-id";
        testNickname = "테스터";

        testMessage = GuildChatMessage.createTextMessage(1L, testUserId, testNickname, "테스트 메시지");
        setId(testMessage, 1L);
    }

    @Nested
    @DisplayName("메시지 전송 테스트")
    class SendMessageTest {

        @Test
        @DisplayName("텍스트 메시지를 전송한다")
        void sendMessage_text_success() {
            // given
            ChatMessageRequest request = ChatMessageRequest.builder()
                .content("안녕하세요!")
                .build();

            when(guildQueryFacadeService.getGuildBasicInfo(1L)).thenReturn(new GuildBasicInfo(1L, "테스트길드", null, 1));
            when(guildQueryFacadeService.isActiveMember(1L, testUserId)).thenReturn(true);
            when(guildQueryFacadeService.getActiveMemberUserIds(1L)).thenReturn(java.util.Collections.emptyList());
            when(chatMessageRepository.save(any(GuildChatMessage.class))).thenAnswer(inv -> {
                GuildChatMessage msg = inv.getArgument(0);
                setId(msg, 1L);
                return msg;
            });

            // when
            ChatMessageResponse response = guildChatService.sendMessage(1L, testUserId, testNickname, request);

            // then
            assertThat(response).isNotNull();
            assertThat(response.getContent()).isEqualTo("안녕하세요!");
            assertThat(response.getMessageType()).isEqualTo(ChatMessageType.TEXT);
            verify(chatMessageRepository).save(any(GuildChatMessage.class));
        }

        @Test
        @DisplayName("이미지 메시지를 전송한다")
        void sendMessage_image_success() {
            // given
            ChatMessageRequest request = ChatMessageRequest.builder()
                .content("이미지 설명")
                .imageUrl("https://example.com/image.jpg")
                .build();

            when(guildQueryFacadeService.getGuildBasicInfo(1L)).thenReturn(new GuildBasicInfo(1L, "테스트길드", null, 1));
            when(guildQueryFacadeService.isActiveMember(1L, testUserId)).thenReturn(true);
            when(guildQueryFacadeService.getActiveMemberUserIds(1L)).thenReturn(java.util.Collections.emptyList());
            when(chatMessageRepository.save(any(GuildChatMessage.class))).thenAnswer(inv -> {
                GuildChatMessage msg = inv.getArgument(0);
                setId(msg, 1L);
                return msg;
            });

            // when
            ChatMessageResponse response = guildChatService.sendMessage(1L, testUserId, testNickname, request);

            // then
            assertThat(response).isNotNull();
            assertThat(response.getMessageType()).isEqualTo(ChatMessageType.IMAGE);
            assertThat(response.getImageUrl()).isEqualTo("https://example.com/image.jpg");
        }

        @Test
        @DisplayName("비멤버는 메시지를 전송할 수 없다")
        void sendMessage_nonMember_fail() {
            // given
            ChatMessageRequest request = ChatMessageRequest.builder()
                .content("안녕하세요!")
                .build();

            when(guildQueryFacadeService.getGuildBasicInfo(1L)).thenReturn(new GuildBasicInfo(1L, "테스트길드", null, 1));
            when(guildQueryFacadeService.isActiveMember(1L, testUserId)).thenReturn(false);

            // when & then
            assertThatThrownBy(() -> guildChatService.sendMessage(1L, testUserId, testNickname, request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("길드 멤버만 채팅에 참여할 수 있습니다");
        }

        @Test
        @DisplayName("존재하지 않는 길드에 메시지 전송 시 예외 발생")
        void sendMessage_guildNotFound_fail() {
            // given
            ChatMessageRequest request = ChatMessageRequest.builder()
                .content("안녕하세요!")
                .build();

            when(guildQueryFacadeService.getGuildBasicInfo(999L)).thenThrow(new IllegalArgumentException("길드를 찾을 수 없습니다"));

            // when & then
            assertThatThrownBy(() -> guildChatService.sendMessage(999L, testUserId, testNickname, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("길드를 찾을 수 없습니다");
        }
    }

    @Nested
    @DisplayName("시스템 메시지 테스트")
    class SystemMessageTest {

        @Test
        @DisplayName("시스템 메시지를 전송한다")
        void sendSystemMessage_success() {
            // given
            when(guildQueryFacadeService.guildExists(1L)).thenReturn(true);
            when(chatMessageRepository.save(any(GuildChatMessage.class))).thenAnswer(inv -> {
                GuildChatMessage msg = inv.getArgument(0);
                setId(msg, 1L);
                return msg;
            });

            // when
            ChatMessageResponse response = guildChatService.sendSystemMessage(
                1L, ChatMessageType.SYSTEM_JOIN, "테스터님이 길드에 가입했습니다."
            );

            // then
            assertThat(response).isNotNull();
            assertThat(response.getMessageType()).isEqualTo(ChatMessageType.SYSTEM_JOIN);
        }

        @Test
        @DisplayName("멤버 가입 알림을 전송한다")
        void notifyMemberJoin_success() {
            // given
            when(guildQueryFacadeService.guildExists(1L)).thenReturn(true);
            when(chatMessageRepository.save(any(GuildChatMessage.class))).thenAnswer(inv -> {
                GuildChatMessage msg = inv.getArgument(0);
                setId(msg, 1L);
                return msg;
            });

            // when
            guildChatService.notifyMemberJoin(1L, "새멤버");

            // then
            verify(chatMessageRepository).save(any(GuildChatMessage.class));
        }

        @Test
        @DisplayName("멤버 탈퇴 알림을 전송한다")
        void notifyMemberLeave_success() {
            // given
            when(guildQueryFacadeService.guildExists(1L)).thenReturn(true);
            when(chatMessageRepository.save(any(GuildChatMessage.class))).thenAnswer(inv -> {
                GuildChatMessage msg = inv.getArgument(0);
                setId(msg, 1L);
                return msg;
            });

            // when
            guildChatService.notifyMemberLeave(1L, "탈퇴멤버");

            // then
            verify(chatMessageRepository).save(any(GuildChatMessage.class));
        }
    }

    @Nested
    @DisplayName("메시지 조회 테스트")
    class GetMessagesTest {

        @Test
        @DisplayName("메시지 목록을 조회한다")
        void getMessages_success() {
            // given
            Pageable pageable = PageRequest.of(0, 20);
            Page<GuildChatMessage> messagePage = new PageImpl<>(List.of(testMessage), pageable, 1);

            when(guildQueryFacadeService.isActiveMember(1L, testUserId)).thenReturn(true);
            when(chatMessageRepository.findByGuildIdOrderByCreatedAtDesc(1L, pageable)).thenReturn(messagePage);

            // when
            Page<ChatMessageResponse> result = guildChatService.getMessages(1L, testUserId, pageable);

            // then
            assertThat(result).isNotNull();
            assertThat(result.getContent()).hasSize(1);
        }

        @Test
        @DisplayName("특정 시간 이후의 새 메시지를 조회한다")
        void getNewMessages_success() {
            // given
            LocalDateTime since = LocalDateTime.now().minusMinutes(5);

            when(guildQueryFacadeService.isActiveMember(1L, testUserId)).thenReturn(true);
            when(chatMessageRepository.findNewMessages(1L, since)).thenReturn(List.of(testMessage));

            // when
            List<ChatMessageResponse> result = guildChatService.getNewMessages(1L, testUserId, since);

            // then
            assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("특정 ID 이후의 메시지를 조회한다")
        void getMessagesAfterId_success() {
            // given
            when(guildQueryFacadeService.isActiveMember(1L, testUserId)).thenReturn(true);
            when(chatMessageRepository.findMessagesAfterId(1L, 0L)).thenReturn(List.of(testMessage));

            // when
            List<ChatMessageResponse> result = guildChatService.getMessagesAfterId(1L, testUserId, 0L);

            // then
            assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("비멤버는 메시지를 조회할 수 없다")
        void getMessages_nonMember_fail() {
            // given
            Pageable pageable = PageRequest.of(0, 20);
            when(guildQueryFacadeService.isActiveMember(1L, testUserId)).thenReturn(false);

            // when & then
            assertThatThrownBy(() -> guildChatService.getMessages(1L, testUserId, pageable))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("길드 멤버만 채팅에 참여할 수 있습니다");
        }
    }

    @Nested
    @DisplayName("메시지 삭제 테스트")
    class DeleteMessageTest {

        @Test
        @DisplayName("본인 메시지를 삭제한다")
        void deleteMessage_byOwner_success() {
            // given
            when(chatMessageRepository.findById(1L)).thenReturn(Optional.of(testMessage));

            // when
            guildChatService.deleteMessage(1L, 1L, testUserId);

            // then
            assertThat(testMessage.getIsDeleted()).isTrue();
        }

        @Test
        @DisplayName("길드 마스터가 다른 사람 메시지를 삭제할 수 있다")
        void deleteMessage_byMaster_success() {
            // given
            String otherUserId = "other-user-id";
            GuildChatMessage otherMessage = GuildChatMessage.createTextMessage(1L, otherUserId, "다른유저", "다른 메시지");
            setId(otherMessage, 2L);

            when(chatMessageRepository.findById(2L)).thenReturn(Optional.of(otherMessage));
            when(guildQueryFacadeService.isMaster(1L, testUserId)).thenReturn(true);

            // when
            guildChatService.deleteMessage(1L, 2L, testUserId); // masterId = testUserId

            // then
            assertThat(otherMessage.getIsDeleted()).isTrue();
        }

        @Test
        @DisplayName("다른 사람 메시지는 삭제할 수 없다")
        void deleteMessage_byOtherUser_fail() {
            // given
            String otherUserId = "other-user-id";

            GuildChatMessage otherMessage = GuildChatMessage.createTextMessage(2L, "message-owner", "메시지소유자", "다른 메시지");
            setId(otherMessage, 2L);

            when(chatMessageRepository.findById(2L)).thenReturn(Optional.of(otherMessage));
            when(guildQueryFacadeService.isMaster(2L, otherUserId)).thenReturn(false);

            // when & then
            assertThatThrownBy(() -> guildChatService.deleteMessage(2L, 2L, otherUserId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("본인 메시지 또는 길드 마스터만 삭제할 수 있습니다");
        }

        @Test
        @DisplayName("존재하지 않는 메시지 삭제 시 예외 발생")
        void deleteMessage_notFound_fail() {
            // given
            when(chatMessageRepository.findById(999L)).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> guildChatService.deleteMessage(1L, 999L, testUserId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("메시지를 찾을 수 없습니다");
        }
    }

    @Nested
    @DisplayName("메시지 검색 테스트")
    class SearchMessagesTest {

        @Test
        @DisplayName("키워드로 메시지를 검색한다")
        void searchMessages_success() {
            // given
            Pageable pageable = PageRequest.of(0, 20);
            Page<GuildChatMessage> messagePage = new PageImpl<>(List.of(testMessage), pageable, 1);

            when(guildQueryFacadeService.isActiveMember(1L, testUserId)).thenReturn(true);
            when(chatMessageRepository.searchMessages(1L, "테스트", pageable)).thenReturn(messagePage);

            // when
            Page<ChatMessageResponse> result = guildChatService.searchMessages(1L, testUserId, "테스트", pageable);

            // then
            assertThat(result).isNotNull();
            assertThat(result.getContent()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("읽음 상태 테스트")
    class ReadStatusTest {

        @Test
        @DisplayName("메시지를 읽음 처리한다")
        void markAsRead_success() {
            // given
            GuildChatReadStatus readStatus = GuildChatReadStatus.create(1L, testUserId);
            setId(readStatus, 1L);

            when(guildQueryFacadeService.isActiveMember(1L, testUserId)).thenReturn(true);
            when(chatMessageRepository.findById(1L)).thenReturn(Optional.of(testMessage));
            when(readStatusRepository.findByGuildIdAndUserId(1L, testUserId))
                .thenReturn(Optional.of(readStatus));

            // when
            guildChatService.markAsRead(1L, testUserId, 1L);

            // then
            verify(readStatusRepository).findByGuildIdAndUserId(1L, testUserId);
        }

        @Test
        @DisplayName("읽음 상태가 없으면 새로 생성한다")
        void markAsRead_createNewStatus() {
            // given
            when(guildQueryFacadeService.isActiveMember(1L, testUserId)).thenReturn(true);
            when(chatMessageRepository.findById(1L)).thenReturn(Optional.of(testMessage));
            when(readStatusRepository.findByGuildIdAndUserId(1L, testUserId))
                .thenReturn(Optional.empty());
            when(readStatusRepository.save(any(GuildChatReadStatus.class)))
                .thenAnswer(inv -> inv.getArgument(0));

            // when
            guildChatService.markAsRead(1L, testUserId, 1L);

            // then
            verify(readStatusRepository).save(any(GuildChatReadStatus.class));
        }

        @Test
        @DisplayName("다른 길드의 메시지는 읽음 처리할 수 없다")
        void markAsRead_wrongGuild_fail() {
            // given
            GuildChatMessage otherMessage = GuildChatMessage.createTextMessage(2L, testUserId, testNickname, "다른 메시지");
            setId(otherMessage, 2L);

            when(guildQueryFacadeService.isActiveMember(1L, testUserId)).thenReturn(true);
            when(chatMessageRepository.findById(2L)).thenReturn(Optional.of(otherMessage));

            // when & then
            assertThatThrownBy(() -> guildChatService.markAsRead(1L, testUserId, 2L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("해당 길드의 메시지가 아닙니다");
        }

        @Test
        @DisplayName("읽음 상태를 초기화한다")
        void initializeReadStatus_success() {
            // given
            when(guildQueryFacadeService.guildExists(1L)).thenReturn(true);
            when(readStatusRepository.findByGuildIdAndUserId(1L, testUserId))
                .thenReturn(Optional.empty());
            when(readStatusRepository.save(any(GuildChatReadStatus.class)))
                .thenAnswer(inv -> inv.getArgument(0));

            // when
            guildChatService.initializeReadStatus(1L, testUserId);

            // then
            verify(readStatusRepository).save(any(GuildChatReadStatus.class));
        }

        @Test
        @DisplayName("읽음 상태를 삭제한다")
        void deleteReadStatus_success() {
            // when
            guildChatService.deleteReadStatus(1L, testUserId);

            // then
            verify(readStatusRepository).deleteByGuildIdAndUserId(1L, testUserId);
        }
    }

    @Nested
    @DisplayName("채팅 참여 테스트")
    class ChatParticipationTest {

        @Test
        @DisplayName("채팅방에 입장한다")
        void joinChat_success() {
            // given
            when(guildQueryFacadeService.isActiveMember(1L, testUserId)).thenReturn(true);
            when(guildQueryFacadeService.guildExists(1L)).thenReturn(true);
            when(participantRepository.findByGuildIdAndUserId(1L, testUserId))
                .thenReturn(Optional.empty());
            when(participantRepository.save(any(GuildChatParticipant.class)))
                .thenAnswer(inv -> {
                    GuildChatParticipant p = inv.getArgument(0);
                    setId(p, 1L);
                    return p;
                });
            when(chatMessageRepository.save(any(GuildChatMessage.class)))
                .thenAnswer(inv -> {
                    GuildChatMessage msg = inv.getArgument(0);
                    setId(msg, 1L);
                    return msg;
                });
            when(readStatusRepository.findByGuildIdAndUserId(1L, testUserId))
                .thenReturn(Optional.empty());
            when(readStatusRepository.save(any(GuildChatReadStatus.class)))
                .thenAnswer(inv -> inv.getArgument(0));

            // when
            ChatParticipantResponse response = guildChatService.joinChat(1L, testUserId, testNickname);

            // then
            assertThat(response).isNotNull();
            verify(participantRepository).save(any(GuildChatParticipant.class));
        }

        @Test
        @DisplayName("닉네임이 없으면 프로필에서 가져온다")
        void joinChat_withNullNickname_fetchFromProfile() {
            // given
            UserProfileCache profile = new UserProfileCache(
                testUserId, "프로필닉네임", null, 1, null, null, null
            );

            when(guildQueryFacadeService.isActiveMember(1L, testUserId)).thenReturn(true);
            when(guildQueryFacadeService.guildExists(1L)).thenReturn(true);
            when(userProfileCacheService.getUserProfile(testUserId)).thenReturn(profile);
            when(participantRepository.findByGuildIdAndUserId(1L, testUserId))
                .thenReturn(Optional.empty());
            when(participantRepository.save(any(GuildChatParticipant.class)))
                .thenAnswer(inv -> {
                    GuildChatParticipant p = inv.getArgument(0);
                    setId(p, 1L);
                    return p;
                });
            when(chatMessageRepository.save(any(GuildChatMessage.class)))
                .thenAnswer(inv -> {
                    GuildChatMessage msg = inv.getArgument(0);
                    setId(msg, 1L);
                    return msg;
                });
            when(readStatusRepository.findByGuildIdAndUserId(1L, testUserId))
                .thenReturn(Optional.empty());
            when(readStatusRepository.save(any(GuildChatReadStatus.class)))
                .thenAnswer(inv -> inv.getArgument(0));

            // when
            ChatParticipantResponse response = guildChatService.joinChat(1L, testUserId, null);

            // then
            assertThat(response).isNotNull();
            verify(userProfileCacheService).getUserProfile(testUserId);
        }

        @Test
        @DisplayName("채팅방에서 퇴장한다")
        void leaveChat_success() {
            // given
            GuildChatParticipant participant = GuildChatParticipant.create(1L, testUserId, testNickname);
            setId(participant, 1L);

            when(guildQueryFacadeService.isActiveMember(1L, testUserId)).thenReturn(true);
            when(participantRepository.findByGuildIdAndUserId(1L, testUserId))
                .thenReturn(Optional.of(participant));
            when(guildQueryFacadeService.guildExists(1L)).thenReturn(true);
            when(chatMessageRepository.save(any(GuildChatMessage.class)))
                .thenAnswer(inv -> {
                    GuildChatMessage msg = inv.getArgument(0);
                    setId(msg, 1L);
                    return msg;
                });

            // when
            guildChatService.leaveChat(1L, testUserId, testNickname);

            // then
            assertThat(participant.isParticipating()).isFalse();
        }

        @Test
        @DisplayName("참여 여부를 확인한다")
        void isParticipating_true() {
            // given
            when(guildQueryFacadeService.isActiveMember(1L, testUserId)).thenReturn(true);
            when(participantRepository.isParticipating(1L, testUserId)).thenReturn(true);

            // when
            boolean result = guildChatService.isParticipating(1L, testUserId);

            // then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("참여자가 없으면 false 반환")
        void isParticipating_false() {
            // given
            when(guildQueryFacadeService.isActiveMember(1L, testUserId)).thenReturn(true);
            when(participantRepository.isParticipating(1L, testUserId)).thenReturn(false);

            // when
            boolean result = guildChatService.isParticipating(1L, testUserId);

            // then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("활성 참여자 목록을 조회한다")
        void getActiveParticipants_success() {
            // given
            GuildChatParticipant participant = GuildChatParticipant.create(1L, testUserId, testNickname);
            setId(participant, 1L);

            when(guildQueryFacadeService.isActiveMember(1L, testUserId)).thenReturn(true);
            when(participantRepository.findActiveParticipants(1L))
                .thenReturn(List.of(participant));

            // when
            List<ChatParticipantResponse> result = guildChatService.getActiveParticipants(1L, testUserId);

            // then
            assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("참여자 수를 조회한다")
        void getParticipantCount_success() {
            // given
            when(guildQueryFacadeService.isActiveMember(1L, testUserId)).thenReturn(true);
            when(participantRepository.countActiveParticipants(1L)).thenReturn(5L);

            // when
            long count = guildChatService.getParticipantCount(1L, testUserId);

            // then
            assertThat(count).isEqualTo(5L);
        }
    }

    @Nested
    @DisplayName("읽음/안읽음 카운트 테스트")
    class ReadUnreadCountTest {

        @Test
        @DisplayName("읽은 사람 수를 조회한다")
        void getReadCount_success() {
            // given
            when(readStatusRepository.countReadersForMessage(1L, 1L)).thenReturn(3L);

            // when
            int count = guildChatService.getReadCount(1L, 1L);

            // then
            assertThat(count).isEqualTo(3);
        }

        @Test
        @DisplayName("안읽은 사람 수를 조회한다")
        void getUnreadCount_success() {
            // given
            when(participantRepository.countActiveParticipants(1L)).thenReturn(10L);
            when(readStatusRepository.countReadersForMessage(1L, 1L)).thenReturn(3L);

            // when
            int count = guildChatService.getUnreadCount(1L, 1L);

            // then
            assertThat(count).isEqualTo(7);
        }
    }

    @Nested
    @DisplayName("특정 ID 이전 메시지 조회 테스트")
    class GetMessagesBeforeIdTest {

        @Test
        @DisplayName("특정 ID 이전의 메시지를 조회한다")
        void getMessagesBeforeId_success() {
            // given
            Pageable pageable = PageRequest.of(0, 20);
            Page<GuildChatMessage> messagePage = new PageImpl<>(List.of(testMessage), pageable, 1);

            when(guildQueryFacadeService.isActiveMember(1L, testUserId)).thenReturn(true);
            when(chatMessageRepository.findMessagesBeforeId(eq(1L), eq(100L), any(Pageable.class)))
                .thenReturn(messagePage);

            // when
            Page<ChatMessageResponse> result = guildChatService.getMessagesBeforeId(1L, testUserId, 100L, pageable);

            // then
            assertThat(result.getContent()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("알림 메시지 테스트")
    class NotificationTest {

        @Test
        @DisplayName("멤버 강퇴 알림을 전송한다")
        void notifyMemberKick_success() {
            // given
            when(guildQueryFacadeService.guildExists(1L)).thenReturn(true);
            when(chatMessageRepository.save(any(GuildChatMessage.class))).thenAnswer(inv -> {
                GuildChatMessage msg = inv.getArgument(0);
                setId(msg, 1L);
                return msg;
            });

            // when
            guildChatService.notifyMemberKick(1L, "강퇴멤버");

            // then
            verify(chatMessageRepository).save(any(GuildChatMessage.class));
        }

    }

    @Nested
    @DisplayName("참조 포함 시스템 메시지 테스트")
    class SystemMessageWithReferenceTest {

        @Test
        @DisplayName("참조를 포함한 시스템 메시지를 전송한다")
        void sendSystemMessage_withReference_success() {
            // given
            when(guildQueryFacadeService.guildExists(1L)).thenReturn(true);
            when(chatMessageRepository.save(any(GuildChatMessage.class))).thenAnswer(inv -> {
                GuildChatMessage msg = inv.getArgument(0);
                setId(msg, 1L);
                return msg;
            });

            // when
            ChatMessageResponse response = guildChatService.sendSystemMessage(
                1L, ChatMessageType.SYSTEM_ACHIEVEMENT, "업적 달성!", "ACHIEVEMENT", 100L
            );

            // then
            assertThat(response).isNotNull();
            assertThat(response.getMessageType()).isEqualTo(ChatMessageType.SYSTEM_ACHIEVEMENT);
            verify(chatMessageRepository).save(any(GuildChatMessage.class));
        }
    }

    @Nested
    @DisplayName("채팅방 정보 조회 테스트")
    class GetChatRoomInfoTest {

        @Test
        @DisplayName("채팅방 정보를 조회한다")
        void getChatRoomInfo_success() {
            // given
            GuildChatReadStatus readStatus = GuildChatReadStatus.create(1L, testUserId);
            setId(readStatus, 1L);

            when(guildQueryFacadeService.isActiveMember(1L, testUserId)).thenReturn(true);
            when(guildQueryFacadeService.getGuildBasicInfo(1L)).thenReturn(new GuildBasicInfo(1L, "테스트길드", null, 1));
            when(guildQueryFacadeService.getActiveMemberCount(1L)).thenReturn(10);
            when(participantRepository.countActiveParticipants(1L)).thenReturn(5L);
            when(readStatusRepository.findByGuildIdAndUserId(1L, testUserId))
                .thenReturn(Optional.of(readStatus));
            when(readStatusRepository.countUnreadMessagesForUser(eq(1L), anyLong())).thenReturn(3);

            // when
            var result = guildChatService.getChatRoomInfo(1L, testUserId);

            // then
            assertThat(result).isNotNull();
            assertThat(result.getMemberCount()).isEqualTo(10);
            assertThat(result.getParticipantCount()).isEqualTo(5);
            assertThat(result.getUnreadMessageCount()).isEqualTo(3);
        }

        @Test
        @DisplayName("읽음 상태가 없으면 전체 메시지가 안읽은 상태로 표시된다")
        void getChatRoomInfo_noReadStatus_allUnread() {
            // given
            when(guildQueryFacadeService.isActiveMember(1L, testUserId)).thenReturn(true);
            when(guildQueryFacadeService.getGuildBasicInfo(1L)).thenReturn(new GuildBasicInfo(1L, "테스트길드", null, 1));
            when(guildQueryFacadeService.getActiveMemberCount(1L)).thenReturn(10);
            when(participantRepository.countActiveParticipants(1L)).thenReturn(5L);
            when(readStatusRepository.findByGuildIdAndUserId(1L, testUserId))
                .thenReturn(Optional.empty());
            when(chatMessageRepository.countByGuildId(1L)).thenReturn(20L);

            // when
            var result = guildChatService.getChatRoomInfo(1L, testUserId);

            // then
            assertThat(result).isNotNull();
            assertThat(result.getUnreadMessageCount()).isEqualTo(20);
        }
    }

    @Nested
    @DisplayName("안읽은 메시지 수 포함 조회 테스트")
    class GetMessagesWithUnreadCountTest {

        @Test
        @DisplayName("메시지 목록과 함께 안읽은 수를 조회한다")
        void getMessagesWithUnreadCount_success() {
            // given
            Pageable pageable = PageRequest.of(0, 20);
            Page<GuildChatMessage> messagePage = new PageImpl<>(List.of(testMessage), pageable, 1);

            when(guildQueryFacadeService.isActiveMember(1L, testUserId)).thenReturn(true);
            when(chatMessageRepository.findByGuildIdOrderByCreatedAtDesc(1L, pageable))
                .thenReturn(messagePage);
            when(participantRepository.countActiveParticipants(1L)).thenReturn(10L);

            Object[] resultRow = new Object[]{1L, 3L};  // messageId, readCount
            List<Object[]> resultList = new ArrayList<>();
            resultList.add(resultRow);
            when(readStatusRepository.countReadersForMessages(eq(1L), anyList()))
                .thenReturn(resultList);

            // when
            Page<ChatMessageResponse> response = guildChatService.getMessagesWithUnreadCount(1L, testUserId, pageable);

            // then
            assertThat(response).isNotNull();
            assertThat(response.getContent()).hasSize(1);
            assertThat(response.getContent().get(0).getUnreadCount()).isEqualTo(7); // 10 - 3 = 7
        }

        @Test
        @DisplayName("메시지가 없으면 빈 목록을 반환한다")
        void getMessagesWithUnreadCount_emptyMessages() {
            // given
            Pageable pageable = PageRequest.of(0, 20);
            Page<GuildChatMessage> emptyPage = new PageImpl<>(Collections.emptyList(), pageable, 0);

            when(guildQueryFacadeService.isActiveMember(1L, testUserId)).thenReturn(true);
            when(chatMessageRepository.findByGuildIdOrderByCreatedAtDesc(1L, pageable))
                .thenReturn(emptyPage);
            when(participantRepository.countActiveParticipants(1L)).thenReturn(10L);

            // when
            Page<ChatMessageResponse> response = guildChatService.getMessagesWithUnreadCount(1L, testUserId, pageable);

            // then
            assertThat(response.getContent()).isEmpty();
        }
    }

    @Nested
    @DisplayName("닉네임 자동 조회 테스트")
    class AutoNicknameFetchTest {

        @Test
        @DisplayName("빈 닉네임으로 메시지 전송 시 프로필에서 가져온다")
        void sendMessage_withBlankNickname_fetchFromProfile() {
            // given
            ChatMessageRequest request = ChatMessageRequest.builder()
                .content("안녕하세요!")
                .build();
            UserProfileCache profile = new UserProfileCache(
                testUserId, "프로필닉네임", null, 1, null, null, null
            );

            when(guildQueryFacadeService.getGuildBasicInfo(1L)).thenReturn(new GuildBasicInfo(1L, "테스트길드", null, 1));
            when(guildQueryFacadeService.isActiveMember(1L, testUserId)).thenReturn(true);
            when(guildQueryFacadeService.getActiveMemberUserIds(1L)).thenReturn(java.util.Collections.emptyList());
            when(userProfileCacheService.getUserProfile(testUserId)).thenReturn(profile);
            when(chatMessageRepository.save(any(GuildChatMessage.class))).thenAnswer(inv -> {
                GuildChatMessage msg = inv.getArgument(0);
                setId(msg, 1L);
                return msg;
            });

            // when
            ChatMessageResponse response = guildChatService.sendMessage(1L, testUserId, "", request);

            // then
            assertThat(response).isNotNull();
            verify(userProfileCacheService).getUserProfile(testUserId);
        }

        @Test
        @DisplayName("빈 닉네임으로 채팅방 퇴장 시 프로필에서 가져온다")
        void leaveChat_withBlankNickname_fetchFromProfile() {
            // given
            GuildChatParticipant participant = GuildChatParticipant.create(1L, testUserId, testNickname);
            setId(participant, 1L);
            UserProfileCache profile = new UserProfileCache(
                testUserId, "프로필닉네임", null, 1, null, null, null
            );

            when(guildQueryFacadeService.isActiveMember(1L, testUserId)).thenReturn(true);
            when(participantRepository.findByGuildIdAndUserId(1L, testUserId))
                .thenReturn(Optional.of(participant));
            when(guildQueryFacadeService.guildExists(1L)).thenReturn(true);
            when(userProfileCacheService.getUserProfile(testUserId)).thenReturn(profile);
            when(chatMessageRepository.save(any(GuildChatMessage.class)))
                .thenAnswer(inv -> {
                    GuildChatMessage msg = inv.getArgument(0);
                    setId(msg, 1L);
                    return msg;
                });

            // when
            guildChatService.leaveChat(1L, testUserId, "  ");

            // then
            verify(userProfileCacheService).getUserProfile(testUserId);
        }
    }

    @Nested
    @DisplayName("재입장 테스트")
    class RejoinChatTest {

        @Test
        @DisplayName("퇴장한 사용자가 재입장한다")
        void joinChat_rejoin_success() {
            // given
            GuildChatParticipant leftParticipant = GuildChatParticipant.create(1L, testUserId, testNickname);
            leftParticipant.leave();  // 먼저 퇴장
            setId(leftParticipant, 1L);

            when(guildQueryFacadeService.isActiveMember(1L, testUserId)).thenReturn(true);
            when(guildQueryFacadeService.guildExists(1L)).thenReturn(true);
            when(participantRepository.findByGuildIdAndUserId(1L, testUserId))
                .thenReturn(Optional.of(leftParticipant));
            when(chatMessageRepository.save(any(GuildChatMessage.class)))
                .thenAnswer(inv -> {
                    GuildChatMessage msg = inv.getArgument(0);
                    setId(msg, 1L);
                    return msg;
                });
            when(readStatusRepository.findByGuildIdAndUserId(1L, testUserId))
                .thenReturn(Optional.empty());
            when(readStatusRepository.save(any(GuildChatReadStatus.class)))
                .thenAnswer(inv -> inv.getArgument(0));

            // when
            ChatParticipantResponse response = guildChatService.joinChat(1L, testUserId, "재입장닉네임");

            // then
            assertThat(response).isNotNull();
            assertThat(leftParticipant.isParticipating()).isTrue();
            verify(chatMessageRepository).save(any(GuildChatMessage.class)); // 재입장 시스템 메시지
        }
    }

    @Nested
    @DisplayName("메시지 존재 여부 검증 테스트")
    class MessageNotFoundTest {

        @Test
        @DisplayName("존재하지 않는 메시지 읽음 처리 시 예외 발생")
        void markAsRead_messageNotFound_fail() {
            // given
            when(guildQueryFacadeService.isActiveMember(1L, testUserId)).thenReturn(true);
            when(chatMessageRepository.findById(999L)).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> guildChatService.markAsRead(1L, testUserId, 999L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("메시지를 찾을 수 없습니다");
        }
    }

    @Nested
    @DisplayName("길드 메시지 검증 테스트")
    class WrongGuildMessageTest {

        @Test
        @DisplayName("다른 길드의 메시지로 삭제 시 예외 발생")
        void deleteMessage_wrongGuild_fail() {
            // given
            GuildChatMessage otherMessage = GuildChatMessage.createTextMessage(2L, testUserId, testNickname, "다른 메시지");
            setId(otherMessage, 2L);

            when(chatMessageRepository.findById(2L)).thenReturn(Optional.of(otherMessage));

            // when & then
            assertThatThrownBy(() -> guildChatService.deleteMessage(1L, 2L, testUserId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("해당 길드의 메시지가 아닙니다");
        }
    }
}
