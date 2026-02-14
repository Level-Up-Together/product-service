package io.pinkspider.leveluptogethermvp.missionservice.application;

import io.pinkspider.global.api.ApiStatus;
import io.pinkspider.global.event.MissionCommentEvent;
import io.pinkspider.global.exception.CustomException;
import io.pinkspider.leveluptogethermvp.missionservice.domain.dto.MissionCommentRequest;
import io.pinkspider.leveluptogethermvp.missionservice.domain.dto.MissionCommentResponse;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.Mission;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.MissionComment;
import io.pinkspider.leveluptogethermvp.missionservice.infrastructure.MissionCommentRepository;
import io.pinkspider.leveluptogethermvp.missionservice.infrastructure.MissionRepository;
import io.pinkspider.leveluptogethermvp.userservice.profile.application.UserQueryFacadeService;
import io.pinkspider.leveluptogethermvp.userservice.profile.domain.dto.UserProfileCache;
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
    private final UserQueryFacadeService userQueryFacadeService;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 댓글 작성
     */
    @Transactional(transactionManager = "missionTransactionManager")
    public MissionCommentResponse addComment(Long missionId, String userId, MissionCommentRequest request) {
        Mission mission = missionRepository.findById(missionId)
            .orElseThrow(() -> new CustomException(ApiStatus.CLIENT_ERROR.getResultCode(), "미션을 찾을 수 없습니다"));

        // 사용자 프로필 조회 (캐시)
        UserProfileCache userProfile = userQueryFacadeService.getUserProfile(userId);

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
            throw new CustomException(ApiStatus.CLIENT_ERROR.getResultCode(), "미션을 찾을 수 없습니다");
        }

        Pageable pageable = PageRequest.of(page, size);
        Page<MissionComment> comments = missionCommentRepository.findByMissionId(missionId, pageable);

        return comments.map(comment -> MissionCommentResponse.from(comment, currentUserId));
    }

    /**
     * 댓글 삭제 (본인만 가능)
     */
    @Transactional(transactionManager = "missionTransactionManager")
    public void deleteComment(Long missionId, Long commentId, String userId) {
        MissionComment comment = missionCommentRepository.findByIdAndIsDeletedFalse(commentId)
            .orElseThrow(() -> new CustomException(ApiStatus.CLIENT_ERROR.getResultCode(), "댓글을 찾을 수 없습니다"));

        // 해당 미션의 댓글인지 확인
        if (!comment.getMission().getId().equals(missionId)) {
            throw new CustomException(ApiStatus.INVALID_INPUT.getResultCode(), "해당 미션의 댓글이 아닙니다");
        }

        // 본인 댓글만 삭제 가능
        if (!comment.isAuthor(userId)) {
            throw new CustomException(ApiStatus.INVALID_ACCESS.getResultCode(), "본인의 댓글만 삭제할 수 있습니다");
        }

        // Soft delete
        comment.delete();
        missionCommentRepository.save(comment);

        log.info("미션 댓글 삭제: missionId={}, commentId={}, userId={}", missionId, commentId, userId);
    }

    /**
     * 미션의 댓글 수 조회
     */
    public int getCommentCount(Long missionId) {
        return missionCommentRepository.countByMissionId(missionId);
    }
}
