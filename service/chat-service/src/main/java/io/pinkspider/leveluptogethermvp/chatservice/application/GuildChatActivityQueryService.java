package io.pinkspider.leveluptogethermvp.chatservice.application;

import io.pinkspider.leveluptogethermvp.chatservice.domain.enums.ChatMessageType;
import io.pinkspider.leveluptogethermvp.chatservice.infrastructure.GuildChatMessageRepository;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * LUT-853: 길드 단체채팅의 최근 활동 조회 — 길드 홈 목록의 "방금 대화"(카드 '메시지' 표시·정렬)용. BFF 가 부른다.
 *
 * <p>대화 = 사람이 쓴 메시지(TEXT·IMAGE). 가입·탈퇴·미션 같은 시스템 메시지와 삭제된 메시지는 세지 않는다.
 */
@Service
@RequiredArgsConstructor
@Transactional(transactionManager = "chatTransactionManager", readOnly = true)
public class GuildChatActivityQueryService {

    /** 대화로 세는 메시지 종류 — {@link ChatMessageType#isSystemMessage()} 가 false 인 것 */
    private static final List<ChatMessageType> CONVERSATION_TYPES =
            List.of(ChatMessageType.TEXT, ChatMessageType.IMAGE);

    private final GuildChatMessageRepository chatMessageRepository;

    /** 그 시각 이후 단체채팅 대화가 있었던 길드 ID */
    public Set<Long> findGuildIdsWithRecentChat(LocalDateTime since) {
        return new HashSet<>(
                chatMessageRepository.findGuildIdsWithMessagesSince(since, CONVERSATION_TYPES));
    }
}
