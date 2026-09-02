package io.pinkspider.leveluptogethermvp.chatservice.application;

import io.pinkspider.leveluptogethermvp.chatservice.domain.dto.DirectConversationResponse;
import io.pinkspider.leveluptogethermvp.chatservice.domain.dto.DirectMessageRequest;
import io.pinkspider.leveluptogethermvp.chatservice.domain.dto.DirectMessageResponse;
import io.pinkspider.leveluptogethermvp.chatservice.domain.entity.GuildDirectConversation;
import io.pinkspider.leveluptogethermvp.chatservice.domain.entity.GuildDirectMessage;
import io.pinkspider.leveluptogethermvp.chatservice.infrastructure.GuildDirectConversationRepository;
import io.pinkspider.leveluptogethermvp.chatservice.infrastructure.GuildDirectMessageRepository;
import io.pinkspider.leveluptogethermvp.chatservice.realtime.DmRealtimePublisher;
import io.pinkspider.global.event.GuildDirectMessageEvent;
import io.pinkspider.global.facade.GamificationQueryFacade;
import io.pinkspider.global.facade.GuildQueryFacade;
import io.pinkspider.global.facade.UserQueryFacade;
import io.pinkspider.global.facade.dto.DetailedTitleInfoDto;
import io.pinkspider.global.facade.dto.EquippedItemRarityDto;
import io.pinkspider.global.facade.dto.UserProfileInfo;
import io.pinkspider.global.facade.dto.UserTitleDto;
import io.pinkspider.global.translation.TitleNameUtils;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(transactionManager = "chatTransactionManager", readOnly = true)
public class GuildDirectMessageService {

    /** 수신자가 방을 보고 있어도 발신자 에코가 전달되도록 하는 user destination */
    public static final String DM_DESTINATION = "/queue/dm";

    private final GuildDirectConversationRepository conversationRepository;
    private final GuildDirectMessageRepository messageRepository;
    private final GuildQueryFacade guildQueryFacadeService;
    private final UserQueryFacade userQueryFacadeService;
    private final GamificationQueryFacade gamificationQueryFacadeService;
    private final ApplicationEventPublisher eventPublisher;
    private final DmPresenceService dmPresenceService;
    private final DmRealtimePublisher dmRealtimePublisher;

    @Transactional(transactionManager = "chatTransactionManager")
    public DirectMessageResponse sendMessage(
            Long guildId,
            String senderId,
            String recipientId,
            DirectMessageRequest request) {

        validateGuildExists(guildId);
        validateBothAreMember(guildId, senderId, recipientId);
        validateSenderNotBlocking(senderId, recipientId);

        String senderNickname = userQueryFacadeService.getUserNickname(senderId);

        GuildDirectConversation conversation = conversationRepository
            .findConversationIncludingInactive(guildId, senderId, recipientId)
            .orElseGet(() -> {
                GuildDirectConversation newConversation = GuildDirectConversation.create(guildId, senderId, recipientId);
                return conversationRepository.save(newConversation);
            });
        // LUT-287: 탈퇴/재가입으로 비활성화된 대화방은 새 메시지 시점에 재활성화
        if (!conversation.getIsActive()) {
            conversation.activate();
        }

        GuildDirectMessage message;
        if (request.getImageUrl() != null && !request.getImageUrl().isEmpty()) {
            message = GuildDirectMessage.createImageMessage(
                conversation, senderId, senderNickname, request.getContent(), request.getImageUrl());
        } else {
            message = GuildDirectMessage.createTextMessage(
                conversation, senderId, senderNickname, request.getContent());
        }

        GuildDirectMessage savedMessage = messageRepository.save(message);
        conversation.updateLastMessage(request.getContent());

        log.debug("DM 전송: guildId={}, senderId={}, recipientId={}", guildId, senderId, recipientId);

        DirectMessageResponse response = DirectMessageResponse.from(savedMessage);

        // LUT-383: 수신자가 발신자를 차단했으면 저장까지만 하고(발신자 화면은 정상 전송 유지)
        // 수신자 방향의 실시간·알림·푸시를 전부 생략한다 (shadow block).
        boolean hiddenFromRecipient = isHiddenFromRecipient(senderId, recipientId);

        // LUT-263: WS/REST 어느 경로로 보내도 수신자·발신자(다중 디바이스 에코)에게 실시간 전달.
        // Redis pub/sub 릴레이라 상대 세션이 다른 인스턴스에 있어도 전달된다.
        if (!hiddenFromRecipient) {
            dmRealtimePublisher.publishToUser(recipientId, DM_DESTINATION, response);
        }
        dmRealtimePublisher.publishToUser(senderId, DM_DESTINATION, response);

        // LUT-263: 수신자가 이 대화방을 보고 있으면 알림(레코드+레드닷+푸시) 생략 —
        // 실시간 채널로 이미 보고 있는 메시지에 푸시가 오면 대화를 방해한다.
        if (hiddenFromRecipient) {
            log.debug("DM 수신자 전달 생략(차단 관계 shadow block): conversationId={}, senderId={}",
                conversation.getId(), senderId);
        } else if (dmPresenceService.isViewing(recipientId, conversation.getId())) {
            log.debug("DM 알림 생략(수신자 대화방 조회 중): conversationId={}, recipientId={}",
                conversation.getId(), recipientId);
        } else {
            // LUT-224: AFTER_COMMIT 리스너가 알림 레코드 생성 + 실시간 채널 + 푸시를 일괄 처리
            eventPublisher.publishEvent(new GuildDirectMessageEvent(
                senderId, senderNickname, guildId, conversation.getId(),
                savedMessage.getId(), request.getContent(), recipientId));
        }

        return response;
    }

    public List<DirectConversationResponse> getConversations(Long guildId, String userId) {
        validateMembership(guildId, userId);

        List<GuildDirectConversation> conversations = conversationRepository
            .findAllByGuildIdAndUserId(guildId, userId);

        if (conversations.isEmpty()) {
            return List.of();
        }

        List<String> otherUserIds = conversations.stream()
            .map(c -> c.getOtherUserId(userId))
            .distinct()
            .toList();

        // LUT-285: 길드를 탈퇴한 상대(비활성 멤버)와 회원 탈퇴(WITHDRAWN) 상대의 대화는 목록에서 제외.
        // 회원 탈퇴는 길드 멤버십을 정리하지 않아(LUT-287) 멤버 검사만으로는 걸러지지 않는다.
        Set<String> activeMemberIds =
            Set.copyOf(guildQueryFacadeService.getActiveMemberUserIds(guildId));
        Set<String> activeUserIds = Set.copyOf(userQueryFacadeService.getActiveUserIds(otherUserIds));
        // LUT-383: 내가 차단한 상대와의 대화방은 목록에서 숨긴다 — 차단 중 상대(피차단자)가
        // 보낸 새 메시지가 목록 프리뷰·뱃지로도 드러나지 않게 한다. 차단 해제 시 다시 노출된다.
        Set<String> blockedByMe = Set.copyOf(userQueryFacadeService.getBlockedUserIds(userId));

        List<GuildDirectConversation> visibleConversations = conversations.stream()
            .filter(conv -> {
                String otherUserId = conv.getOtherUserId(userId);
                return activeMemberIds.contains(otherUserId)
                    && activeUserIds.contains(otherUserId)
                    && !blockedByMe.contains(otherUserId);
            })
            .toList();

        List<String> visibleOtherUserIds = visibleConversations.stream()
            .map(c -> c.getOtherUserId(userId))
            .distinct()
            .toList();

        Map<String, UserProfileInfo> profileMap =
            userQueryFacadeService.getUserProfiles(visibleOtherUserIds);

        // LUT-443: 상대방 칭호·장착 아이템 등급 배치 조회 (썸네일 스파크용, 실패 시 빈 값 폴백)
        Map<String, List<UserTitleDto>> titlesMap = loadEquippedTitles(visibleOtherUserIds);
        Map<String, List<EquippedItemRarityDto>> itemRarityMap =
            loadEquippedItemRarities(visibleOtherUserIds);

        return visibleConversations.stream()
            .map(conv -> {
                String otherUserId = conv.getOtherUserId(userId);
                UserProfileInfo otherProfile = profileMap.get(otherUserId);
                String otherNickname = otherProfile != null ? otherProfile.nickname() : "알 수 없음";
                String otherProfileImage = otherProfile != null ? otherProfile.picture() : null;
                int unreadCount = messageRepository.countUnreadMessages(conv.getId(), userId);
                DirectConversationResponse response = DirectConversationResponse.from(
                    conv, userId, otherNickname, otherProfileImage, unreadCount);
                enrichOtherUserRarities(response, otherUserId, titlesMap, itemRarityMap);
                return response;
            })
            .toList();
    }

    /** LUT-443: 상대방 칭호 등급 배치 조회 — 실패해도 DM 목록 자체는 내려간다 */
    private Map<String, List<UserTitleDto>> loadEquippedTitles(List<String> userIds) {
        try {
            return gamificationQueryFacadeService.getEquippedTitlesByUserIds(userIds);
        } catch (Exception e) {
            log.warn("DM 칭호 등급 배치 조회 실패: error={}", e.getMessage());
            return Map.of();
        }
    }

    /** LUT-443: 상대방 장착 아이템 희귀도 배치 조회 — 실패 시 빈 배열 유지 */
    private Map<String, List<EquippedItemRarityDto>> loadEquippedItemRarities(List<String> userIds) {
        try {
            return gamificationQueryFacadeService.getEquippedItemRaritiesByUserIds(userIds);
        } catch (Exception e) {
            log.warn("DM 장착 아이템 희귀도 배치 조회 실패: error={}", e.getMessage());
            return Map.of();
        }
    }

    private void enrichOtherUserRarities(
            DirectConversationResponse response,
            String otherUserId,
            Map<String, List<UserTitleDto>> titlesMap,
            Map<String, List<EquippedItemRarityDto>> itemRarityMap) {
        DetailedTitleInfoDto titleInfo = TitleNameUtils.buildDetailedTitleInfo(
            titlesMap.getOrDefault(otherUserId, List.of()), null);
        response.setOtherUserLeftTitleRarity(titleInfo.leftRarity());
        response.setOtherUserRightTitleRarity(titleInfo.rightRarity());
        response.setOtherUserEquippedItemRarities(
            itemRarityMap.getOrDefault(otherUserId, List.of()));
    }

    public Page<DirectMessageResponse> getMessages(
            Long guildId,
            String userId,
            String otherUserId,
            Pageable pageable) {

        validateMembership(guildId, userId);

        GuildDirectConversation conversation = conversationRepository
            .findConversation(guildId, userId, otherUserId)
            .orElseThrow(() -> new IllegalArgumentException("대화를 찾을 수 없습니다."));

        return messageRepository.findByConversationId(conversation.getId(), pageable)
            .map(DirectMessageResponse::from);
    }

    public Page<DirectMessageResponse> getMessagesByConversationId(
            Long guildId,
            String userId,
            Long conversationId,
            Pageable pageable) {

        validateMembership(guildId, userId);

        GuildDirectConversation conversation = conversationRepository.findById(conversationId)
            .orElseThrow(() -> new IllegalArgumentException("대화를 찾을 수 없습니다."));

        if (!conversation.isParticipant(userId)) {
            throw new IllegalStateException("해당 대화에 접근할 수 없습니다.");
        }

        if (!conversation.getGuildId().equals(guildId)) {
            throw new IllegalArgumentException("해당 길드의 대화가 아닙니다.");
        }

        return messageRepository.findByConversationId(conversationId, pageable)
            .map(DirectMessageResponse::from);
    }

    public Page<DirectMessageResponse> getMessagesBeforeId(
            Long guildId,
            String userId,
            Long conversationId,
            Long beforeId,
            Pageable pageable) {

        validateMembership(guildId, userId);
        validateConversationAccess(conversationId, userId, guildId);

        return messageRepository.findMessagesBeforeId(conversationId, beforeId, pageable)
            .map(DirectMessageResponse::from);
    }

    @Transactional(transactionManager = "chatTransactionManager")
    public void markAsRead(Long guildId, String userId, Long conversationId) {
        validateMembership(guildId, userId);
        GuildDirectConversation conversation =
            validateConversationAccess(conversationId, userId, guildId);

        // LUT-383: 내가 차단한 상대의 방은 읽음 처리하지 않는다 — 피차단자 화면의 '안읽음'
        // 표시가 읽음으로 바뀌면 수신 사실이 새어나가 조용한 차단이 깨진다.
        if (userQueryFacadeService.getBlockedUserIds(userId)
                .contains(conversation.getOtherUserId(userId))) {
            log.debug("DM 읽음 처리 생략(차단 상대): conversationId={}, userId={}", conversationId, userId);
            return;
        }

        int updatedCount = messageRepository.markAllAsRead(conversationId, userId);
        // LUT-263: 읽음 처리는 방을 보고 있다는 신호이므로 presence도 갱신
        dmPresenceService.markViewing(userId, conversationId);
        log.debug("DM 읽음 처리: conversationId={}, userId={}, count={}", conversationId, userId, updatedCount);
    }

    public int getTotalUnreadCount(Long guildId, String userId) {
        validateMembership(guildId, userId);
        // LUT-383: 차단한 상대가 보낸 미읽음은 뱃지에서 제외 (빈 목록 오동작 방지용 __none__ 센티널)
        List<String> blockedByMe = userQueryFacadeService.getBlockedUserIds(userId);
        List<String> excludedSenderIds = blockedByMe.isEmpty() ? List.of("__none__") : blockedByMe;
        return messageRepository.countTotalUnreadMessages(guildId, userId, excludedSenderIds);
    }

    @Transactional(transactionManager = "chatTransactionManager")
    public DirectConversationResponse getOrCreateConversation(Long guildId, String userId, String otherUserId) {
        validateGuildExists(guildId);
        validateBothAreMember(guildId, userId, otherUserId);
        validateSenderNotBlocking(userId, otherUserId);

        GuildDirectConversation conversation = conversationRepository
            .findConversationIncludingInactive(guildId, userId, otherUserId)
            .orElseGet(() -> {
                GuildDirectConversation newConversation = GuildDirectConversation.create(guildId, userId, otherUserId);
                return conversationRepository.save(newConversation);
            });
        // LUT-287: 탈퇴/재가입으로 비활성화된 대화방은 대화 재시작 시점에 재활성화
        if (!conversation.getIsActive()) {
            conversation.activate();
        }

        UserProfileInfo otherProfile = userQueryFacadeService.getUserProfile(otherUserId);
        String otherNickname = otherProfile != null ? otherProfile.nickname() : "알 수 없음";
        String otherProfileImage = otherProfile != null ? otherProfile.picture() : null;
        int unreadCount = messageRepository.countUnreadMessages(conversation.getId(), userId);

        DirectConversationResponse response = DirectConversationResponse.from(
            conversation, userId, otherNickname, otherProfileImage, unreadCount);
        // LUT-443: 방 생성/조회 응답에도 동일하게 등급 정보 포함 (단건이므로 단일 원소 배치)
        enrichOtherUserRarities(response, otherUserId,
            loadEquippedTitles(List.of(otherUserId)),
            loadEquippedItemRarities(List.of(otherUserId)));
        return response;
    }

    @Transactional(transactionManager = "chatTransactionManager")
    public void deleteMessage(Long guildId, String userId, Long messageId) {
        validateMembership(guildId, userId);

        GuildDirectMessage message = messageRepository.findById(messageId)
            .orElseThrow(() -> new IllegalArgumentException("메시지를 찾을 수 없습니다."));

        if (!message.getSenderId().equals(userId)) {
            throw new IllegalStateException("본인 메시지만 삭제할 수 있습니다.");
        }

        if (!message.getConversation().getGuildId().equals(guildId)) {
            throw new IllegalArgumentException("해당 길드의 메시지가 아닙니다.");
        }

        message.delete();
        log.info("DM 삭제: messageId={}, deletedBy={}", messageId, userId);
    }

    /**
     * 회원 탈퇴 시 유저가 참여한 모든 DM 대화방 비활성화 (LUT-287)
     *
     * @return 비활성화된 대화방 수
     */
    @Transactional(transactionManager = "chatTransactionManager")
    public int deactivateConversationsForUser(String userId) {
        List<GuildDirectConversation> conversations =
            conversationRepository.findAllActiveByUserId(userId);
        conversations.forEach(GuildDirectConversation::deactivate);
        return conversations.size();
    }

    /**
     * 길드 탈퇴/추방 시 해당 길드에서 유저가 참여한 DM 대화방 비활성화 (LUT-287)
     *
     * @return 비활성화된 대화방 수
     */
    @Transactional(transactionManager = "chatTransactionManager")
    public int deactivateConversations(Long guildId, String userId) {
        List<GuildDirectConversation> conversations =
            conversationRepository.findAllByGuildIdAndUserId(guildId, userId);
        conversations.forEach(GuildDirectConversation::deactivate);
        return conversations.size();
    }

    // ============ 헬퍼 메서드 ============

    private void validateGuildExists(Long guildId) {
        if (!guildQueryFacadeService.guildExists(guildId)) {
            throw new IllegalArgumentException("길드를 찾을 수 없습니다: " + guildId);
        }
    }

    /**
     * LUT-383: 조용한 차단(shadow block) — 발신자가 상대를 차단한 경우에만 에러를 낸다
     * (차단자는 자신의 차단 사실을 이미 안다). 발신자가 차단당한 쪽이면 그대로 통과시키되,
     * 전송 경로에서 수신자 노출(실시간·알림·목록·뱃지)만 조용히 걷어낸다 — 피차단자 화면에는
     * 정상 전송으로 보이지만 차단자에게는 어디에도 도달하지 않는다.
     */
    private void validateSenderNotBlocking(String senderId, String recipientId) {
        if (userQueryFacadeService.getBlockedUserIds(senderId).contains(recipientId)) {
            throw new io.pinkspider.global.exception.CustomException("400", "error.dm.blocked_user");
        }
    }

    /** LUT-383: 수신자가 발신자를 차단했는지 — true 면 수신자 측 노출을 전부 생략한다 */
    private boolean isHiddenFromRecipient(String senderId, String recipientId) {
        return userQueryFacadeService.getBlockedUserIds(recipientId).contains(senderId);
    }

    private void validateMembership(Long guildId, String userId) {
        if (!guildQueryFacadeService.isActiveMember(guildId, userId)) {
            throw new IllegalStateException("길드 멤버만 DM을 사용할 수 있습니다.");
        }
    }

    private void validateBothAreMember(Long guildId, String userId1, String userId2) {
        if (userId1.equals(userId2)) {
            throw new IllegalArgumentException("자기 자신에게 DM을 보낼 수 없습니다.");
        }
        if (!guildQueryFacadeService.isActiveMember(guildId, userId1)) {
            throw new IllegalStateException("발신자가 길드 멤버가 아닙니다.");
        }
        if (!guildQueryFacadeService.isActiveMember(guildId, userId2)) {
            throw new IllegalStateException("수신자가 길드 멤버가 아닙니다.");
        }
        // LUT-285: 회원 탈퇴자는 길드 멤버십이 정리되지 않아(LUT-287) 위 멤버 검사를 통과한다.
        // 유저 상태(ACTIVE)를 별도로 확인해 탈퇴 회원에게 DM이 전송되는 것을 막는다.
        if (userQueryFacadeService.getActiveUserIds(List.of(userId2)).isEmpty()) {
            throw new IllegalStateException("탈퇴한 회원에게는 DM을 보낼 수 없습니다.");
        }
    }

    private GuildDirectConversation validateConversationAccess(
            Long conversationId, String userId, Long guildId) {
        GuildDirectConversation conversation = conversationRepository.findById(conversationId)
            .orElseThrow(() -> new IllegalArgumentException("대화를 찾을 수 없습니다."));

        if (!conversation.isParticipant(userId)) {
            throw new IllegalStateException("해당 대화에 접근할 수 없습니다.");
        }

        if (!conversation.getGuildId().equals(guildId)) {
            throw new IllegalArgumentException("해당 길드의 대화가 아닙니다.");
        }
        return conversation;
    }

}
