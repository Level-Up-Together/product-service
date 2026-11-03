package io.pinkspider.leveluptogethermvp.chatservice.application;

import io.pinkspider.global.exception.CustomException;
import io.pinkspider.global.facade.GuildQueryFacade;
import io.pinkspider.global.facade.UserQueryFacade;
import io.pinkspider.global.moderation.annotation.ModerateImage;
import io.pinkspider.leveluptogethermvp.chatservice.domain.dto.ChatImageUploadResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

/**
 * 길드 채팅·DM 이미지 업로드 (LUT-590).
 *
 * <p>업로드는 전송과 분리된 2단계다 — ① 여기서 파일을 저장하고 URL 을 돌려준다 ② 클라이언트가 그 URL 을 기존 전송 API 에 실어 보낸다. 전송 API·데이터
 * 모델은 원래 {@code image_url} 을 받고 있었고 업로드 경로만 없었다. 권한 규칙은 전송과 같다(길드원만, DM 은 발신자가 상대를 차단했으면 거부).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatImageService {

    private final ChatImageStorageService storageService;
    private final GuildQueryFacade guildQueryFacadeService;
    private final UserQueryFacade userQueryFacadeService;

    @ModerateImage
    public ChatImageUploadResponse uploadGuildChatImage(
            Long guildId, String userId, MultipartFile imageFile) {
        validateGuildExists(guildId);
        validateMember(guildId, userId);
        return new ChatImageUploadResponse(storageService.store(imageFile, guildId, userId));
    }

    @ModerateImage
    public ChatImageUploadResponse uploadDirectMessageImage(
            Long guildId, String userId, String recipientId, MultipartFile imageFile) {
        validateGuildExists(guildId);
        validateMember(guildId, userId);
        validateMember(guildId, recipientId);
        // LUT-383 과 같은 조용한 차단 규칙 — 발신자가 상대를 차단한 경우만 거부한다
        if (userQueryFacadeService.getBlockedUserIds(userId).contains(recipientId)) {
            throw new CustomException("400", "error.dm.blocked_user");
        }
        return new ChatImageUploadResponse(storageService.store(imageFile, guildId, userId));
    }

    /**
     * 전송 API 가 받은 {@code image_url} 검증. 비어 있으면 통과(텍스트 메시지), 값이 있으면 **보낸 사람 본인이 이 길드 채팅용으로 업로드한**
     * URL 이어야 한다 — 외부 URL 추적 이미지 주입과, 남의 사진 URL 로 보낸 메시지를 지워 그 파일을 삭제하는 것을 막는다.
     */
    public void validateImageUrl(String imageUrl, Long guildId, String senderId) {
        if (imageUrl == null || imageUrl.isEmpty()) {
            return;
        }
        if (!storageService.isOwnedBy(imageUrl, guildId, senderId)) {
            log.warn("채팅 이미지 URL 거부(업로드 API 미경유): {}", imageUrl);
            throw new CustomException("CHAT_IMAGE_004", "error.chat.invalid_image_url");
        }
    }

    /**
     * 메시지 삭제 시 저장된 이미지 정리. 삭제 트랜잭션이 커밋된 뒤에만 지운다 — 롤백되면 메시지는 남는데 파일만 사라지는 일을 막는다. 트랜잭션 밖이면 즉시 지운다.
     */
    public void deleteAfterCommit(String imageUrl, Long guildId, String senderId) {
        if (imageUrl == null || imageUrl.isEmpty()) {
            return;
        }
        // 메시지 발신자 본인 경로의 파일만 지운다 — 검증 도입 전 저장된 메시지가 남의 URL 을 가리켜도 안전하다
        if (!storageService.isOwnedBy(imageUrl, guildId, senderId)) {
            log.warn("채팅 이미지 삭제 생략(발신자 경로 아님): {}", imageUrl);
            return;
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            storageService.delete(imageUrl);
                        }
                    });
        } else {
            storageService.delete(imageUrl);
        }
    }

    private void validateGuildExists(Long guildId) {
        if (!guildQueryFacadeService.guildExists(guildId)) {
            throw new IllegalArgumentException("길드를 찾을 수 없습니다: " + guildId);
        }
    }

    private void validateMember(Long guildId, String userId) {
        if (!guildQueryFacadeService.isActiveMember(guildId, userId)) {
            throw new IllegalStateException("길드 멤버만 채팅에 참여할 수 있습니다.");
        }
    }
}
