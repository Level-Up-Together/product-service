package io.pinkspider.leveluptogethermvp.missionservice.application;

import io.pinkspider.global.api.ApiStatus;
import io.pinkspider.global.event.MissionCommentDeletedEvent;
import io.pinkspider.global.event.MissionCommentEvent;
import io.pinkspider.global.exception.CustomException;
import io.pinkspider.leveluptogethermvp.missionservice.domain.dto.MissionCommentRequest;
import io.pinkspider.leveluptogethermvp.missionservice.domain.dto.MissionCommentResponse;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.Mission;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.MissionComment;
import io.pinkspider.leveluptogethermvp.missionservice.infrastructure.MissionCommentRepository;
import io.pinkspider.leveluptogethermvp.missionservice.infrastructure.MissionRepository;
import io.pinkspider.global.facade.UserQueryFacade;
import io.pinkspider.global.facade.dto.UserProfileInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(transactionManager = "missionTransactionManager", readOnly = true)
public class MissionCommentService {

    private final MissionCommentRepository missionCommentRepository;
    private final MissionRepository missionRepository;
    private final UserQueryFacade userQueryFacadeService;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 댓글 작성
     */
    @Transactional(transactionManager = "missionTransactionManager")
    public MissionCommentResponse addComment(Long missionId, String userId, MissionCommentRequest request) {
        Mission mission = missionRepository.findByIdAndIsDeletedFalse(missionId)
            .orElseThrow(() -> new CustomException(ApiStatus.CLIENT_ERROR.getResultCode(), "error.mission.not_found"));

        // 사용자 프로필 조회 (캐시)
        UserProfileInfo userProfile = userQueryFacadeService.getUserProfile(userId);

        // 댓글 생성
        MissionComment comment = MissionComment.builder()
            .mission(mission)
            .userId(userId)
            .userNickname(userProfile.nickname())
            .userProfileImageUrl(userProfile.picture())
            .userLevel(userProfile.level())
            .content(request.getContent())
            .isDeleted(false)
            .build();

        MissionComment saved = missionCommentRepository.save(comment);

        // 미션 생성자에게 알림 이벤트 발행 (본인 댓글 제외)
        if (!userId.equals(mission.getCreatorId())) {
            eventPublisher.publishEvent(new MissionCommentEvent(
                userId,
                mission.getCreatorId(),
                userProfile.nickname(),
                missionId,
                mission.getTitle()
            ));
        }

        log.info("미션 댓글 작성: missionId={}, commentId={}, userId={}", missionId, saved.getId(), userId);

        return MissionCommentResponse.from(saved, userId);
    }

    /**
     * 댓글 목록 조회 (페이징)
     */
    public Page<MissionCommentResponse> getComments(Long missionId, String currentUserId, int page, int size) {
        // 미션 존재 확인
        if (!missionRepository.existsById(missionId)) {
            throw new CustomException(ApiStatus.CLIENT_ERROR.getResultCode(), "error.mission.not_found");
        }

        Pageable pageable = PageRequest.of(page, size);
        Page<MissionComment> comments = missionCommentRepository.findByMissionId(missionId, pageable);

        return comments.map(comment -> MissionCommentResponse.from(comment, currentUserId));
    }

    /**
     * 어드민 댓글 강제 삭제 (신고 처리용).
     * 본인 검증 없이 삭제하며, missionId 검증도 생략 (commentId만으로 식별).
     */
    @Transactional(transactionManager = "missionTransactionManager")
    public void deleteCommentByAdmin(Long commentId, String reason) {
        MissionComment comment = missionCommentRepository.findByIdAndIsDeletedFalse(commentId)
            .orElseThrow(() -> new CustomException(ApiStatus.CLIENT_ERROR.getResultCode(), "error.mission.comment.not_found"));

        comment.delete();
        missionCommentRepository.save(comment);

        publishCommentDeletedEvent(comment);
        log.info("미션 댓글 어드민 삭제: commentId={}, reason={}", commentId, reason);
    }

    /**
     * 댓글 삭제 (본인만 가능)
     */
    @Transactional(transactionManager = "missionTransactionManager")
    public void deleteComment(Long missionId, Long commentId, String userId) {
        MissionComment comment = missionCommentRepository.findByIdAndIsDeletedFalse(commentId)
            .orElseThrow(() -> new CustomException(ApiStatus.CLIENT_ERROR.getResultCode(), "error.mission.comment.not_found"));

        // 해당 미션의 댓글인지 확인
        if (!comment.getMission().getId().equals(missionId)) {
            throw new CustomException(ApiStatus.INVALID_INPUT.getResultCode(), "error.mission.comment.wrong_mission");
        }

        // 본인 댓글만 삭제 가능
        if (!comment.isAuthor(userId)) {
            throw new CustomException(ApiStatus.INVALID_ACCESS.getResultCode(), "error.mission.comment.not_owner");
        }

        // Soft delete
        comment.delete();
        missionCommentRepository.save(comment);

        publishCommentDeletedEvent(comment);
        log.info("미션 댓글 삭제: missionId={}, commentId={}, userId={}", missionId, commentId, userId);
    }

    /**
     * LUT-418: 받은 댓글 카운터 감소 이벤트 발행.
     * 작성 시 MissionCommentEvent 발행 조건(작성자 != 미션 생성자)과 정확히 대칭이어야
     * 카운터가 음수 방향으로 드리프트하지 않는다 (본인 댓글은 작성 시에도 카운트되지 않음).
     * 조회가 findByIdAndIsDeletedFalse 라 이중 삭제로 인한 이중 발행은 구조적으로 차단된다.
     */
    private void publishCommentDeletedEvent(MissionComment comment) {
        String creatorId = comment.getMission().getCreatorId();
        if (!comment.getUserId().equals(creatorId)) {
            eventPublisher.publishEvent(new MissionCommentDeletedEvent(
                comment.getUserId(),
                creatorId,
                comment.getMission().getId()
            ));
        }
    }

    /**
     * 미션의 댓글 수 조회
     */
    public int getCommentCount(Long missionId) {
        return missionCommentRepository.countByMissionId(missionId);
    }
}
