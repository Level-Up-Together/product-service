package io.pinkspider.leveluptogethermvp.userservice.mypage.application;

import io.pinkspider.leveluptogethermvp.metaservice.userlevelconfig.application.UserLevelConfigCacheService;
import io.pinkspider.global.event.UserProfileChangedEvent;
import io.pinkspider.global.exception.CustomException;
import io.pinkspider.global.facade.GuildQueryFacade;
import io.pinkspider.global.facade.dto.GuildMembershipInfo;
import io.pinkspider.leveluptogethermvp.metaservice.userlevelconfig.domain.entity.UserLevelConfig;
import io.pinkspider.global.facade.GamificationQueryFacade;
import io.pinkspider.global.facade.dto.TitleChangeResultDto;
import io.pinkspider.global.facade.dto.UserExperienceDto;
import io.pinkspider.global.facade.dto.UserStatsDto;
import io.pinkspider.global.facade.dto.UserTitleDto;
import io.pinkspider.global.enums.TitlePosition;
import io.pinkspider.global.enums.TitleRarity;
import io.pinkspider.leveluptogethermvp.userservice.friend.domain.entity.Friendship;
import io.pinkspider.leveluptogethermvp.userservice.friend.domain.enums.FriendshipStatus;
import io.pinkspider.leveluptogethermvp.userservice.friend.infrastructure.FriendshipRepository;
import io.pinkspider.global.moderation.annotation.ModerateImage;
import io.pinkspider.leveluptogethermvp.userservice.mypage.domain.dto.MyPageResponse;
import io.pinkspider.leveluptogethermvp.userservice.mypage.domain.dto.MyPageResponse.EquippedTitleInfo;
import io.pinkspider.leveluptogethermvp.userservice.mypage.domain.dto.MyPageResponse.ExperienceInfo;
import io.pinkspider.leveluptogethermvp.userservice.mypage.domain.dto.MyPageResponse.ProfileInfo;
import io.pinkspider.leveluptogethermvp.userservice.mypage.domain.dto.MyPageResponse.UserInfo;
import io.pinkspider.leveluptogethermvp.userservice.mypage.domain.dto.ProfileUpdateRequest;
import io.pinkspider.leveluptogethermvp.userservice.mypage.domain.dto.PublicProfileResponse;
import io.pinkspider.leveluptogethermvp.userservice.mypage.domain.dto.TitleChangeRequest;
import io.pinkspider.leveluptogethermvp.userservice.mypage.domain.dto.TitleChangeResponse;
import io.pinkspider.leveluptogethermvp.userservice.mypage.domain.dto.UserTitleListResponse;
import io.pinkspider.leveluptogethermvp.userservice.mypage.domain.dto.UserTitleListResponse.UserTitleItem;
import io.pinkspider.global.enums.ReportTargetType;
import io.pinkspider.global.domain.ContentReviewChecker;
import io.pinkspider.global.facade.dto.UserProfileInfo;
import io.pinkspider.leveluptogethermvp.userservice.profile.application.UserProfileCacheService;
import io.pinkspider.leveluptogethermvp.userservice.unit.user.domain.entity.Users;
import io.pinkspider.leveluptogethermvp.userservice.unit.user.infrastructure.UserRepository;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MyPageService {

    private final UserRepository userRepository;
    private final GamificationQueryFacade gamificationQueryFacadeService;
    private final FriendshipRepository friendshipRepository;
    private final UserLevelConfigCacheService userLevelConfigCacheService;
    private final ProfileImageStorageService profileImageStorageService;
    private final GuildQueryFacade guildQueryFacadeService;
    private final ContentReviewChecker contentReviewChecker;
    private final ApplicationEventPublisher eventPublisher;
    private final UserProfileCacheService userProfileCacheService;

    /**
     * MyPage 전체 데이터 조회
     */
    public MyPageResponse getMyPage(String userId) {
        Users user = findUserOrThrow(userId);

        return MyPageResponse.builder()
            .profile(buildProfileInfo(user, userId))
            .experience(buildExperienceInfo(userId))
            .userInfo(buildUserInfo(user, userId))
            .build();
    }

    /**
     * 공개 프로필 조회 (타인이 볼 수 있는 정보)
     *
     * @param targetUserId  조회할 사용자 ID
     * @param currentUserId 현재 로그인한 사용자 ID (null 가능)
     * @return 공개 프로필 정보
     */
    public PublicProfileResponse getPublicProfile(String targetUserId, String currentUserId) {
        Users user = findUserOrThrow(targetUserId);

        // 장착된 칭호 조회
        List<UserTitleDto> equippedTitles = gamificationQueryFacadeService.getEquippedTitlesByUserId(targetUserId);
        PublicProfileResponse.EquippedTitleInfo leftTitle = null;
        PublicProfileResponse.EquippedTitleInfo rightTitle = null;

        for (UserTitleDto ut : equippedTitles) {
            if (ut.equippedPosition() == TitlePosition.LEFT) {
                leftTitle = toPublicEquippedTitleInfo(ut);
            } else if (ut.equippedPosition() == TitlePosition.RIGHT) {
                rightTitle = toPublicEquippedTitleInfo(ut);
            }
        }

        // 레벨 정보
        int level = gamificationQueryFacadeService.getUserLevel(targetUserId);

        // 통계 정보
        UserStatsDto stats = gamificationQueryFacadeService.getOrCreateUserStats(targetUserId);

        LocalDate startDate = user.getCreatedAt() != null
            ? user.getCreatedAt().toLocalDate()
            : LocalDate.now();
        long daysSinceJoined = ChronoUnit.DAYS.between(startDate, LocalDate.now()) + 1;

        // 보유 칭호 수
        long titlesCount = gamificationQueryFacadeService.countUserTitles(targetUserId);

        // 소속 길드 목록 조회
        List<GuildMembershipInfo> guildMemberships = guildQueryFacadeService.getUserGuildMemberships(targetUserId);
        List<Long> guildIds = guildMemberships.stream().map(GuildMembershipInfo::guildId).toList();
        Map<Long, Integer> memberCountMap = guildQueryFacadeService.countActiveMembersByGuildIds(guildIds);
        List<PublicProfileResponse.GuildInfo> guilds = guildMemberships.stream()
            .map(m -> toGuildInfo(m, memberCountMap.getOrDefault(m.guildId(), 0)))
            .collect(Collectors.toList());

        // 본인 여부
        boolean isOwner = targetUserId.equals(currentUserId);
        log.debug("getPublicProfile: targetUserId={}, currentUserId={}, isOwner={}",
            targetUserId, currentUserId, isOwner);

        // 친구 관계 상태 조회 (본인이 아니고 로그인한 경우에만)
        String friendshipStatusStr = "NONE";
        Long friendRequestId = null;
        if (!isOwner && currentUserId != null) {
            try {
                Optional<Friendship> friendshipOpt = friendshipRepository.findFriendship(currentUserId, targetUserId);
                if (friendshipOpt.isPresent()) {
                    Friendship friendship = friendshipOpt.get();
                    FriendshipStatus status = friendship.getStatus();
                    if (status == FriendshipStatus.ACCEPTED) {
                        friendshipStatusStr = "ACCEPTED";
                    } else if (status == FriendshipStatus.PENDING) {
                        // 내가 보낸 요청인지, 받은 요청인지 구분
                        if (friendship.getUserId().equals(currentUserId)) {
                            friendshipStatusStr = "PENDING_SENT";
                        } else {
                            friendshipStatusStr = "PENDING_RECEIVED";
                            friendRequestId = friendship.getId();
                        }
                    } else if (status == FriendshipStatus.REJECTED) {
                        // 거절된 경우: 새로운 친구 요청 가능 (NONE으로 표시)
                        friendshipStatusStr = "NONE";
                        log.debug("Friendship was rejected: currentUserId={}, targetUserId={}", currentUserId, targetUserId);
                    } else if (status == FriendshipStatus.BLOCKED) {
                        // 차단된 경우
                        friendshipStatusStr = "BLOCKED";
                    }
                }
            } catch (Exception e) {
                log.error("친구 관계 조회 중 오류 발생: currentUserId={}, targetUserId={}, error={}",
                    currentUserId, targetUserId, e.getMessage(), e);
                // 오류 발생 시 기본값 NONE 유지
            }
        }

        // 신고 처리중 여부 확인
        boolean isUnderReview = contentReviewChecker.isUnderReview(ReportTargetType.USER_PROFILE, targetUserId);

        return PublicProfileResponse.builder()
            .userId(targetUserId)
            .nickname(user.getDisplayName())
            .profileImageUrl(user.getPicture())
            .bio(user.getBio())
            .leftTitle(leftTitle)
            .rightTitle(rightTitle)
            .level(level)
            .startDate(startDate)
            .daysSinceJoined(daysSinceJoined)
            .clearedMissionsCount(stats.totalMissionCompletions())
            .acquiredTitlesCount((int) titlesCount)
            .guilds(guilds)
            .isOwner(isOwner)
            .friendshipStatus(friendshipStatusStr)
            .friendRequestId(friendRequestId)
            .isUnderReview(isUnderReview)
            .build();
    }

    /**
     * 자기소개 수정
     *
     * @param userId 사용자 ID
     * @param bio    새 자기소개
     * @return 업데이트된 프로필 정보
     */
    @Transactional
    public ProfileInfo updateBio(String userId, String bio) {
        Users user = findUserOrThrow(userId);

        // 길이 검증
        if (bio != null && bio.length() > 200) {
            throw new CustomException("BIO_001", "자기소개는 200자 이하여야 합니다.");
        }

        user.updateBio(bio);
        userRepository.save(user);

        log.info("자기소개 변경: userId={}", userId);

        return buildProfileInfo(user, userId);
    }

    /**
     * 프로필 이미지 변경 (URL 직접 지정)
     */
    @Transactional
    public ProfileInfo updateProfileImage(String userId, ProfileUpdateRequest request) {
        Users user = findUserOrThrow(userId);

        user.updatePicture(request.getProfileImageUrl());
        userRepository.save(user);

        userProfileCacheService.evictUserProfileCache(userId);
        int level = gamificationQueryFacadeService.getUserLevel(userId);
        eventPublisher.publishEvent(new UserProfileChangedEvent(
            userId, user.getNickname(), request.getProfileImageUrl(), level));

        log.info("프로필 이미지 변경: userId={}", userId);

        return buildProfileInfo(user, userId);
    }

    /**
     * 프로필 이미지 업로드
     *
     * @param userId    사용자 ID
     * @param imageFile 업로드할 이미지 파일
     * @return 업데이트된 프로필 정보
     */
    @ModerateImage
    @Transactional
    public ProfileInfo uploadProfileImage(String userId, MultipartFile imageFile) {
        Users user = findUserOrThrow(userId);

        // 유효성 검증
        if (!profileImageStorageService.isValidImage(imageFile)) {
            throw new CustomException("PROFILE_002", "유효하지 않은 이미지 파일입니다. (허용 확장자: jpg, jpeg, png, gif, webp / 최대 5MB)");
        }

        // 기존 이미지 삭제 (로컬 저장 이미지만)
        String oldImageUrl = user.getPicture();
        if (oldImageUrl != null) {
            profileImageStorageService.delete(oldImageUrl);
        }

        // 새 이미지 저장
        String newImageUrl = profileImageStorageService.store(imageFile, userId);

        // 사용자 정보 업데이트
        user.updatePicture(newImageUrl);
        userRepository.save(user);

        userProfileCacheService.evictUserProfileCache(userId);
        int level = gamificationQueryFacadeService.getUserLevel(userId);
        eventPublisher.publishEvent(new UserProfileChangedEvent(
            userId, user.getNickname(), newImageUrl, level));

        log.info("프로필 이미지 업로드: userId={}, newImageUrl={}", userId, newImageUrl);

        return buildProfileInfo(user, userId);
    }

    /**
     * 보유 칭호 목록 조회
     */
    public UserTitleListResponse getUserTitles(String userId) {
        List<UserTitleDto> userTitles = gamificationQueryFacadeService.getUserTitlesWithTitleInfo(userId);

        Long equippedLeftId = null;
        Long equippedRightId = null;

        for (UserTitleDto ut : userTitles) {
            if (ut.isEquipped() && ut.equippedPosition() != null) {
                if (ut.equippedPosition() == TitlePosition.LEFT) {
                    equippedLeftId = ut.id();
                } else if (ut.equippedPosition() == TitlePosition.RIGHT) {
                    equippedRightId = ut.id();
                }
            }
        }

        List<UserTitleItem> titleItems = userTitles.stream()
            .map(this::toUserTitleItem)
            .collect(Collectors.toList());

        return UserTitleListResponse.builder()
            .totalCount(titleItems.size())
            .titles(titleItems)
            .equippedLeftId(equippedLeftId)
            .equippedRightId(equippedRightId)
            .build();
    }

    /**
     * 닉네임 중복 확인
     *
     * @param nickname 확인할 닉네임
     * @param userId   현재 사용자 ID (자신은 제외, null이면 전체 검사)
     * @return 사용 가능 여부
     */
    public boolean isNicknameAvailable(String nickname, String userId) {
        if (nickname == null || nickname.trim().isEmpty()) {
            return false;
        }

        if (userId != null) {
            // 자신 제외하고 중복 확인 (수정 시)
            return !userRepository.existsByNicknameAndIdNot(nickname, userId);
        } else {
            // 전체 중복 확인 (회원가입 시)
            return !userRepository.existsByNickname(nickname);
        }
    }

    /**
     * 닉네임 변경
     *
     * @param userId   사용자 ID
     * @param nickname 새 닉네임
     * @return 업데이트된 프로필 정보
     */
    @Transactional
    public ProfileInfo updateNickname(String userId, String nickname) {
        // 닉네임 유효성 검사
        validateNickname(nickname);

        // 중복 확인
        if (!isNicknameAvailable(nickname, userId)) {
            throw new CustomException("NICKNAME_001", "이미 사용 중인 닉네임입니다.");
        }

        Users user = findUserOrThrow(userId);
        user.updateNickname(nickname);
        userRepository.save(user);

        userProfileCacheService.evictUserProfileCache(userId);
        int level = gamificationQueryFacadeService.getUserLevel(userId);
        eventPublisher.publishEvent(new UserProfileChangedEvent(
            userId, nickname, user.getPicture(), level));

        log.info("닉네임 변경: userId={}, newNickname={}", userId, nickname);

        return buildProfileInfo(user, userId);
    }

    /**
     * 닉네임 설정 필요 여부 확인 OAuth 가입 시 닉네임이 중복되어 자동 생성된 경우 true 반환
     *
     * @param userId 사용자 ID
     * @return 닉네임 설정 필요 여부
     */
    public boolean needsNicknameSetup(String userId) {
        Users user = findUserOrThrow(userId);
        return !user.isNicknameSet();
    }

    /**
     * 닉네임 유효성 검사
     */
    private void validateNickname(String nickname) {
        if (nickname == null || nickname.trim().isEmpty()) {
            throw new CustomException("NICKNAME_002", "닉네임을 입력해주세요.");
        }
        if (nickname.length() < 2) {
            throw new CustomException("NICKNAME_003", "닉네임은 2자 이상이어야 합니다.");
        }
        if (nickname.length() > 10) {
            throw new CustomException("NICKNAME_004", "닉네임은 10자 이하여야 합니다.");
        }
        // 한글, 영문, 숫자, 아랍어 허용
        if (!nickname.matches("^[가-힣a-zA-Z0-9\u0600-\u06FF]+$")) {
            throw new CustomException("NICKNAME_005", "닉네임은 한글, 영문, 숫자, 아랍어만 사용 가능합니다.");
        }
    }

    /**
     * 칭호 변경 (좌측/우측 동시 변경) — TitleService에 위임
     */
    public TitleChangeResponse changeTitles(String userId, TitleChangeRequest request) {
        TitleChangeResultDto result = gamificationQueryFacadeService.changeTitles(
            userId, request.getLeftUserTitleId(), request.getRightUserTitleId());

        return TitleChangeResponse.builder()
            .message("칭호가 변경되었습니다.")
            .leftTitle(toEquippedTitleInfo(result.leftTitle()))
            .rightTitle(toEquippedTitleInfo(result.rightTitle()))
            .build();
    }

    /**
     * 회원 탈퇴
     *
     * @param userId 사용자 ID
     */
    @Transactional
    public void withdrawUser(String userId) {
        Users user = findUserOrThrow(userId);

        // 이미 탈퇴한 사용자인지 확인
        if (user.getStatus() == io.pinkspider.leveluptogethermvp.userservice.unit.user.domain.enums.UserStatus.WITHDRAWN) {
            throw new CustomException("USER_002", "이미 탈퇴한 사용자입니다.");
        }

        // 프로필 이미지 삭제
        if (user.getPicture() != null) {
            profileImageStorageService.delete(user.getPicture());
        }

        // 사용자 상태를 WITHDRAWN으로 변경
        user.withdraw();
        userRepository.save(user);

        log.info("회원 탈퇴 처리 완료: userId={}", userId);
    }

    // ============== Private Helper Methods ==============

    private Users findUserOrThrow(String userId) {
        return userRepository.findById(userId)
            .orElseThrow(() -> new CustomException("USER_001", "사용자를 찾을 수 없습니다."));
    }

    private ProfileInfo buildProfileInfo(Users user, String userId) {
        // 장착된 칭호 조회
        List<UserTitleDto> equippedTitles = gamificationQueryFacadeService.getEquippedTitlesByUserId(userId);

        EquippedTitleInfo leftTitle = null;
        EquippedTitleInfo rightTitle = null;

        for (UserTitleDto ut : equippedTitles) {
            if (ut.equippedPosition() == TitlePosition.LEFT) {
                leftTitle = toEquippedTitleInfo(ut);
            } else if (ut.equippedPosition() == TitlePosition.RIGHT) {
                rightTitle = toEquippedTitleInfo(ut);
            }
        }

        // 팔로워/팔로잉 수 (친구 수로 대체)
        int friendCount = friendshipRepository.countFriends(userId);

        return ProfileInfo.builder()
            .userId(userId)
            .nickname(user.getDisplayName())
            .profileImageUrl(user.getPicture())
            .bio(user.getBio())
            .leftTitle(leftTitle)
            .rightTitle(rightTitle)
            .followerCount(friendCount)
            .followingCount(friendCount)
            .build();
    }

    private ExperienceInfo buildExperienceInfo(String userId) {
        UserExperienceDto userExp = gamificationQueryFacadeService.getOrCreateUserExperience(userId);

        Integer nextLevelRequiredExp = getNextLevelRequiredExp(userExp.currentLevel());

        // EXP 퍼센테이지 계산: (totalExp 마지막 3자리 / 다음 레벨 필요 경험치) * 100
        // 요구사항: 누적 경험치 하위 3자리 / 다음 레벨에 필요한 경험치 * 100 = 퍼센트
        int expForPercentage = userExp.totalExp() % 1000;  // 마지막 3자리
        double expPercentage = nextLevelRequiredExp != null && nextLevelRequiredExp > 0
            ? (double) expForPercentage / nextLevelRequiredExp * 100
            : 0;

        return ExperienceInfo.builder()
            .currentLevel(userExp.currentLevel())
            .currentExp(userExp.currentExp())
            .totalExp(userExp.totalExp())
            .nextLevelRequiredExp(nextLevelRequiredExp)
            .expPercentage(Math.min(100, expPercentage))
            .expForPercentage(expForPercentage)
            .build();
    }

    private UserInfo buildUserInfo(Users user, String userId) {
        UserStatsDto stats = gamificationQueryFacadeService.getOrCreateUserStats(userId);

        // 가입일 (createdAt)
        LocalDate startDate = user.getCreatedAt() != null
            ? user.getCreatedAt().toLocalDate()
            : LocalDate.now();

        // 가입 일수 계산
        long daysSinceJoined = ChronoUnit.DAYS.between(startDate, LocalDate.now()) + 1;

        // 랭킹 퍼센타일 계산 (상위 X%)
        Double rankingPercentile = gamificationQueryFacadeService.calculateRankingPercentile(stats.rankingPoints());

        // 보유 칭호 수
        long titlesCount = gamificationQueryFacadeService.countUserTitles(userId);

        return UserInfo.builder()
            .startDate(startDate)
            .daysSinceJoined(daysSinceJoined)
            .clearedMissionsCount(stats.totalMissionCompletions())
            .clearedMissionBooksCount(stats.totalMissionFullCompletions())
            .rankingPercentile(rankingPercentile)
            .acquiredTitlesCount((int) titlesCount)
            .rankingPoints(stats.rankingPoints())
            .build();
    }

    private EquippedTitleInfo toEquippedTitleInfo(UserTitleDto userTitle) {
        return EquippedTitleInfo.builder()
            .userTitleId(userTitle.id())
            .titleId(userTitle.titleId())
            .name(userTitle.titleName())
            .nameEn(userTitle.titleNameEn())
            .nameAr(userTitle.titleNameAr())
            .displayName(userTitle.titleName())
            .rarity(userTitle.titleRarity() != null ? userTitle.titleRarity().name() : null)
            .colorCode(userTitle.titleColorCode())
            .iconUrl(userTitle.titleIconUrl())
            .build();
    }

    private PublicProfileResponse.EquippedTitleInfo toPublicEquippedTitleInfo(UserTitleDto userTitle) {
        return PublicProfileResponse.EquippedTitleInfo.builder()
            .titleId(userTitle.titleId())
            .name(userTitle.titleName())
            .nameEn(userTitle.titleNameEn())
            .nameAr(userTitle.titleNameAr())
            .displayName(userTitle.titleName())
            .rarity(userTitle.titleRarity() != null ? userTitle.titleRarity().name() : null)
            .colorCode(userTitle.titleColorCode())
            .iconUrl(userTitle.titleIconUrl())
            .build();
    }

    private PublicProfileResponse.GuildInfo toGuildInfo(GuildMembershipInfo membership, int memberCount) {
        return PublicProfileResponse.GuildInfo.builder()
            .guildId(membership.guildId())
            .name(membership.guildName())
            .imageUrl(membership.guildImageUrl())
            .level(membership.guildLevel())
            .memberCount(memberCount)
            .build();
    }

    private UserTitleItem toUserTitleItem(UserTitleDto userTitle) {
        return UserTitleItem.builder()
            .userTitleId(userTitle.id())
            .titleId(userTitle.titleId())
            .name(userTitle.titleName())
            .nameEn(userTitle.titleNameEn())
            .nameAr(userTitle.titleNameAr())
            .displayName(userTitle.titleName())
            .description(userTitle.titleDescription())
            .descriptionEn(userTitle.titleDescriptionEn())
            .descriptionAr(userTitle.titleDescriptionAr())
            .rarity(userTitle.titleRarity() != null ? userTitle.titleRarity().name() : null)
            .positionType(userTitle.titlePositionType() != null ? userTitle.titlePositionType().name() : null)
            .colorCode(userTitle.titleColorCode())
            .iconUrl(userTitle.titleIconUrl())
            .isEquipped(userTitle.isEquipped())
            .equippedPosition(userTitle.equippedPosition() != null
                ? userTitle.equippedPosition().name()
                : null)
            .acquiredAt(userTitle.acquiredAt())
            .build();
    }

    private Integer getNextLevelRequiredExp(int currentLevel) {
        UserLevelConfig config = userLevelConfigCacheService.getLevelConfigByLevel(currentLevel);
        return config != null ? config.getRequiredExp() : 100 + (currentLevel - 1) * 50;  // 기본 공식
    }
}
