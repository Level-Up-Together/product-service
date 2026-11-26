package io.pinkspider.leveluptogethermvp.chatservice.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.pinkspider.leveluptogethermvp.chatservice.domain.enums.ChatMessageType;
import io.pinkspider.leveluptogethermvp.chatservice.infrastructure.GuildChatMessageRepository;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("GuildChatActivityQueryService — 길드 홈 '방금 대화' (LUT-853)")
class GuildChatActivityQueryServiceTest {

    @Mock private GuildChatMessageRepository chatMessageRepository;

    @InjectMocks private GuildChatActivityQueryService service;

    @Test
    @DisplayName("그 시각 이후 대화가 있었던 길드 ID 를 집합으로 돌려준다")
    void returnsGuildIds() {
        LocalDateTime since = LocalDateTime.of(2026, 11, 20, 1, 0);
        when(chatMessageRepository.findGuildIdsWithMessagesSince(eq(since), any()))
                .thenReturn(List.of(3L, 7L));

        Set<Long> result = service.findGuildIdsWithRecentChat(since);

        assertThat(result).containsExactlyInAnyOrder(3L, 7L);
    }

    @Test
    @DisplayName("사람이 쓴 종류(TEXT·IMAGE)만 대화로 센다 — 시스템 메시지는 조회 대상이 아니다")
    void countsOnlyConversationTypes() {
        LocalDateTime since = LocalDateTime.of(2026, 11, 20, 1, 0);
        when(chatMessageRepository.findGuildIdsWithMessagesSince(eq(since), any()))
                .thenReturn(List.of());

        assertThat(service.findGuildIdsWithRecentChat(since)).isEmpty();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<ChatMessageType>> types =
                ArgumentCaptor.forClass(Collection.class);
        verify(chatMessageRepository).findGuildIdsWithMessagesSince(eq(since), types.capture());
        assertThat(types.getValue())
                .containsExactlyInAnyOrder(ChatMessageType.TEXT, ChatMessageType.IMAGE);
        assertThat(types.getValue()).noneMatch(ChatMessageType::isSystemMessage);
        // 대화가 아닌 종류는 전부 시스템 메시지다 — 새 종류가 생기면 여기서 다시 판단한다
        assertThat(ChatMessageType.values())
                .filteredOn(type -> !types.getValue().contains(type))
                .allMatch(ChatMessageType::isSystemMessage);
    }
}
