package io.pinkspider.leveluptogethermvp.userservice.mypage.application;

import io.pinkspider.global.exception.CustomException;
import io.pinkspider.leveluptogethermvp.metaservice.domain.entity.LevelConfig;
import io.pinkspider.leveluptogethermvp.metaservice.infrastructure.LevelConfigRepository;
import io.pinkspider.leveluptogethermvp.userservice.achievement.domain.entity.Title;
import io.pinkspider.leveluptogethermvp.userservice.achievement.domain.entity.UserTitle;
import io.pinkspider.leveluptogethermvp.userservice.achievement.domain.enums.TitlePosition;
import io.pinkspider.leveluptogethermvp.userservice.achievement.domain.enums.TitleRarity;
import io.pinkspider.leveluptogethermvp.userservice.achievement.infrastructure.UserTitleRepository;
import io.pinkspider.leveluptogethermvp.userservice.feed.infrastructure.ActivityFeedRepository;
import io.pinkspider.leveluptogethermvp.guildservice.domain.entity.Guild;
import io.pinkspider.leveluptogethermvp.guildservice.domain.entity.GuildMember;
import io.pinkspider.leveluptogethermvp.guildservice.infrastructure.GuildMemberRepository;
import io.pinkspider.leveluptogethermvp.userservice.friend.domain.entity.Friendship;
import io.pinkspider.leveluptogethermvp.userservice.friend.domain.enums.FriendshipStatus;
import io.pinkspider.leveluptogethermvp.userservice.friend.infrastructure.FriendshipRepository;
import io.pinkspider.leveluptogethermvp.userservice.moderation.application.ImageModerationService;
import io.pinkspider.leveluptogethermvp.userservice.moderation.domain.dto.ImageModerationResult;
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
import io.pinkspider.leveluptogethermvp.userservice.achievement.domain.entity.UserStats;
import org.springframework.web.multipart.MultipartFile;
import io.pinkspider.leveluptogethermvp.userservice.achievement.infrastructure.UserStatsRepository;
import io.pinkspider.leveluptogethermvp.userservice.unit.user.domain.entity.UserExperience;
import io.pinkspider.leveluptogethermvp.userservice.unit.user.domain.entity.Users;
import io.pinkspider.leveluptogethermvp.userservice.unit.user.infrastructure.UserExperienceRepository;
import io.pinkspider.leveluptogethermvp.userservice.unit.user.infrastructure.UserRepository;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MyPageService {

    private final UserRepository userRepository;
    private final UserExperienceRepository userExperienceRepository;
    private final UserTitleRepository userTitleRepository;
    private final UserStatsRepository userStatsRepository;
    private final FriendshipRepository friendshipRepository;
    private final LevelConfigRepository levelConfigRepository;
    private final ProfileImageStorageService profileImageStorageService;
    private final ImageModerationService imageModerationService;
    private final ActivityFeedRepository activityFeedRepository;
    private final GuildMemberRepository guildMemberRepository;

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
     * @param targetUserId 조회할 사용자 ID
     * @param currentUserId 현재 로그인한 사용자 ID (null 가능)
     * @return 공개 프로필 정보
     */
    public PublicProfileResponse getPublicProfile(String targetUserId, String currentUserId) {
        Users user = findUserOrThrow(targetUserId);

        // 장착된 칭호 조회
        List<UserTitle> equippedTitles = userTitleRepository.findEquippedTitlesByUserId(targetUserId);
        PublicProfileResponse.EquippedTitleInfo leftTitle = null;
        PublicProfileResponse.EquippedTitleInfo rightTitle = null;

        for (UserTitle ut : equippedTitles) {
            if (ut.getEquippedPosition() == TitlePosition.LEFT) {
                leftTitle = toPublicEquippedTitleInfo(ut);
            } else if (ut.getEquippedPosition() == TitlePosition.RIGHT) {
                rightTitle = toPublicEquippedTitleInfo(ut);
            }
        }

        // 레벨 정보
        UserExperience userExp = userExperienceRepository.findByUserId(targetUserId)
            .orElse(UserExperience.builder().currentLevel(1).build());

        // 통계 정보
        UserStats stats = userStatsRepository.findByUserId(targetUserId)
            .orElse(UserStats.builder()
                .totalMissionCompletions(0)
                .totalTitlesAcquired(0)
                .build());

        LocalDate startDate = user.getCreatedAt() != null
            ? user.getCreatedAt().toLocalDate()
            : LocalDate.now();
        long daysSinceJoined = ChronoUnit.DAYS.between(startDate, LocalDate.now()) + 1;

        // 보유 칭호 수
        long titlesCount = userTitleRepository.countByUserId(targetUserId);

        // 소속 길드 목록 조회
        List<GuildMember> guildMemberships = guildMemberRepository.findAllActiveGuildMemberships(targetUserId);
        List<PublicProfileResponse.GuildInfo> guilds = guildMemberships.stream()
            .map(this::toGuildInfo)
            .collect(Collectors.toList());

        // 본인 여부
        boolean isOwner = targetUserId.equals(currentUserId);
        log.debug("getPublicProfile: targetUserId={}, currentUserId={}, isOwner={}",
            targetUserId, currentUserId, isOwner);

        // 친구 관계 상태 조회 (본인이 아니고 로그인한 경우에만)
        String friendshipStatusStr = "NONE";
        Long friendRequestId = null;
        if (!isOwner && currentUserId != null) {
            Optional<Friendship> friendshipOpt = friendshipRepository.findFriendship(currentUserId, targetUserId);
            if (friendshipOpt.isPresent()) {
                Friendship friendship = friendshipOpt.get();
                if (friendship.getStatus() == FriendshipStatus.ACCEPTED) {
                    friendshipStatusStr = "ACCEPTED";
                } else if (friendship.getStatus() == FriendshipStatus.PENDING) {
                    // 내가 보낸 요청인지, 받은 요청인지 구분
                    if (friendship.getUserId().equals(currentUserId)) {
                        friendshipStatusStr = "PENDING_SENT";
                    } else {
                        friendshipStatusStr = "PENDING_RECEIVED";
                        friendRequestId = friendship.getId();
                    }
                }
            }
        }

        return PublicProfileResponse.builder()
            .userId(targetUserId)
            .nickname(user.getDisplayName())
            .profileImageUrl(user.getPicture())
            .bio(user.getBio())
            .leftTitle(leftTitle)
            .rightTitle(rightTitle)
            .level(userExp.getCurrentLevel())
            .startDate(startDate)
            .daysSinceJoined(daysSinceJoined)
            .clearedMissionsCount(stats.getTotalMissionCompletions())
            .acquiredTitlesCount((int) titlesCount)
            .guilds(guilds)
            .isOwner(isOwner)
            .friendshipStatus(friendshipStatusStr)
            .friendRequestId(friendRequestId)
            .build();
    }

    /**
     * 자기소개 수정
     *
     * @param userId 사용자 ID
     * @param bio 새 자기소개
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

        log.info("프로필 이미지 변경: userId={}", userId);

        return buildProfileInfo(user, userId);
    }

    /**
     * 프로필 이미지 업로드
     *
     * @param userId 사용자 ID
     * @param imageFile 업로드할 이미지 파일
     * @return 업데이트된 프로필 정보
     */
    @Transactional
    public ProfileInfo uploadProfileImage(String userId, MultipartFile imageFile) {
        Users user = findUserOrThrow(userId);

        // 유효성 검증
        if (!profileImageStorageService.isValidImage(imageFile)) {
            throw new CustomException("PROFILE_002", "유효하지 않은 이미지 파일입니다. (허용 확장자: jpg, jpeg, png, gif, webp / 최대 5MB)");
        }

        // 이미지 콘텐츠 검증 (부적절한 이미지 필터링)
        if (imageModerationService.isEnabled()) {
            ImageModerationResult moderationResult = imageModerationService.analyzeImage(imageFile);
            if (!moderationResult.isSafe()) {
                log.warn("부적절한 이미지 업로드 시도 차단: userId={}, 사유={}",
                    userId, moderationResult.getRejectionReason());
                throw new CustomException("PROFILE_006", "부적절한 이미지가 감지되었습니다. 다른 이미지를 사용해주세요.");
            }
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

        log.info("프로필 이미지 업로드: userId={}, newImageUrl={}", userId, newImageUrl);

        return buildProfileInfo(user, userId);
    }

    /**
     * 보유 칭호 목록 조회
     */
    public UserTitleListResponse getUserTitles(String userId) {
        List<UserTitle> userTitles = userTitleRepository.findByUserIdWithTitle(userId);

        Long equippedLeftId = null;
        Long equippedRightId = null;

        for (UserTitle ut : userTitles) {
            if (ut.getIsEquipped() && ut.getEquippedPosition() != null) {
                if (ut.getEquippedPosition() == TitlePosition.LEFT) {
                    equippedLeftId = ut.getId();
                } else if (ut.getEquippedPosition() == TitlePosition.RIGHT) {
                    equippedRightId = ut.getId();
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
     * @param userId 현재 사용자 ID (자신은 제외, null이면 전체 검사)
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
     * @param userId 사용자 ID
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

        log.info("닉네임 변경: userId={}, newNickname={}", userId, nickname);

        return buildProfileInfo(user, userId);
    }

    /**
     * 닉네임 설정 필요 여부 확인
     * OAuth 가입 시 닉네임이 중복되어 자동 생성된 경우 true 반환
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
        // 한글, 영문, 숫자만 허용
        if (!nickname.matches("^[가-힣a-zA-Z0-9]+$")) {
            throw new CustomException("NICKNAME_005", "닉네임은 한글, 영문, 숫자만 사용 가능합니다.");
        }
    }

    /**
     * 칭호 변경 (좌측/우측 동시 변경)
     */
    @Transactional
    public TitleChangeResponse changeTitles(String userId, TitleChangeRequest request) {
        // 좌측/우측 칭호가 같으면 에러
        if (request.getLeftUserTitleId().equals(request.getRightUserTitleId())) {
            throw new CustomException("TITLE_001", "좌측과 우측에 같은 칭호를 설정할 수 없습니다.");
        }

        // 칭호 존재 여부 및 소유권 사전 검증
        if (!userTitleRepository.existsById(request.getLeftUserTitleId())) {
            throw new CustomException("TITLE_002", "좌측 칭호를 찾을 수 없습니다.");
        }
        if (!userTitleRepository.existsById(request.getRightUserTitleId())) {
            throw new CustomException("TITLE_002", "우측 칭호를 찾을 수 없습니다.");
        }

        // 기존 장착 해제 (clearAutomatically=true로 영속성 컨텍스트 클리어됨)
        userTitleRepository.unequipAllByUserId(userId);

        // 영속성 컨텍스트가 클리어되었으므로 엔티티 다시 조회
        UserTitle leftUserTitle = userTitleRepository.findById(request.getLeftUserTitleId())
            .orElseThrow(() -> new CustomException("TITLE_002", "좌측 칭호를 찾을 수 없습니다."));

        if (!leftUserTitle.getUserId().equals(userId)) {
            throw new CustomException("TITLE_003", "본인의 칭호만 장착할 수 있습니다.");
        }

        UserTitle rightUserTitle = userTitleRepository.findById(request.getRightUserTitleId())
            .orElseThrow(() -> new CustomException("TITLE_002", "우측 칭호를 찾을 수 없습니다."));

        if (!rightUserTitle.getUserId().equals(userId)) {
            throw new CustomException("TITLE_003", "본인의 칭호만 장착할 수 있습니다.");
        }

        // 새로운 칭호 장착
        leftUserTitle.equip(TitlePosition.LEFT);
        rightUserTitle.equip(TitlePosition.RIGHT);

        userTitleRepository.save(leftUserTitle);
        userTitleRepository.save(rightUserTitle);

        // 피드의 칭호도 업데이트
        String combinedTitle = leftUserTitle.getTitle().getDisplayName() + " " + rightUserTitle.getTitle().getDisplayName();
        TitleRarity highestRarity = getHighestRarity(leftUserTitle.getTitle().getRarity(), rightUserTitle.getTitle().getRarity());
        int updatedCount = activityFeedRepository.updateUserTitleByUserId(userId, combinedTitle, highestRarity);

        log.info("칭호 변경: userId={}, leftTitleId={}, rightTitleId={}, feedsUpdated={}",
            userId, leftUserTitle.getTitle().getId(), rightUserTitle.getTitle().getId(), updatedCount);

        return TitleChangeResponse.builder()
            .message("칭호가 변경되었습니다.")
            .leftTitle(toEquippedTitleInfo(leftUserTitle))
            .rightTitle(toEquippedTitleInfo(rightUserTitle))
            .build();
    }

    // ============== Private Helper Methods ==============

    private Users findUserOrThrow(String userId) {
        return userRepository.findById(userId)
            .orElseThrow(() -> new CustomException("USER_001", "사용자를 찾을 수 없습니다."));
    }

    private ProfileInfo buildProfileInfo(Users user, String userId) {
        // 장착된 칭호 조회
        List<UserTitle> equippedTitles = userTitleRepository.findEquippedTitlesByUserId(userId);

        EquippedTitleInfo leftTitle = null;
        EquippedTitleInfo rightTitle = null;

        for (UserTitle ut : equippedTitles) {
            if (ut.getEquippedPosition() == TitlePosition.LEFT) {
                leftTitle = toEquippedTitleInfo(ut);
            } else if (ut.getEquippedPosition() == TitlePosition.RIGHT) {
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
        UserExperience userExp = userExperienceRepository.findByUserId(userId)
            .orElse(UserExperience.builder()
                .userId(userId)
                .currentLevel(1)
                .currentExp(0)
                .totalExp(0)
                .build());

        Integer nextLevelRequiredExp = getNextLevelRequiredExp(userExp.getCurrentLevel());

        // EXP 퍼센테이지 계산: (totalExp 마지막 3자리 / 다음 레벨 필요 경험치) * 100
        // 요구사항: 누적 경험치 하위 3자리 / 다음 레벨에 필요한 경험치 * 100 = 퍼센트
        int expForPercentage = userExp.getTotalExp() % 1000;  // 마지막 3자리
        double expPercentage = nextLevelRequiredExp != null && nextLevelRequiredExp > 0
            ? (double) expForPercentage / nextLevelRequiredExp * 100
            : 0;

        return ExperienceInfo.builder()
            .currentLevel(userExp.getCurrentLevel())
            .currentExp(userExp.getCurrentExp())
            .totalExp(userExp.getTotalExp())
            .nextLevelRequiredExp(nextLevelRequiredExp)
            .expPercentage(Math.min(100, expPercentage))
            .expForPercentage(expForPercentage)
            .build();
    }

    private UserInfo buildUserInfo(Users user, String userId) {
        UserStats stats = userStatsRepository.findByUserId(userId)
            .orElse(UserStats.builder()
                .userId(userId)
                .totalMissionCompletions(0)
                .totalMissionFullCompletions(0)
                .totalTitlesAcquired(0)
                .rankingPoints(0L)
                .build());

        // 가입일 (createdAt)
        LocalDate startDate = user.getCreatedAt() != null
            ? user.getCreatedAt().toLocalDate()
            : LocalDate.now();

        // 가입 일수 계산
        long daysSinceJoined = ChronoUnit.DAYS.between(startDate, LocalDate.now()) + 1;

        // 랭킹 퍼센타일 계산 (상위 X%)
        Double rankingPercentile = calculateRankingPercentile(stats.getRankingPoints());

        // 보유 칭호 수
        long titlesCount = userTitleRepository.countByUserId(userId);

        return UserInfo.builder()
            .startDate(startDate)
            .daysSinceJoined(daysSinceJoined)
            .clearedMissionsCount(stats.getTotalMissionCompletions())
            .clearedMissionBooksCount(stats.getTotalMissionFullCompletions())
            .rankingPercentile(rankingPercentile)
            .acquiredTitlesCount((int) titlesCount)
            .rankingPoints(stats.getRankingPoints())
            .build();
    }

    private EquippedTitleInfo toEquippedTitleInfo(UserTitle userTitle) {
        Title title = userTitle.getTitle();
        return EquippedTitleInfo.builder()
            .userTitleId(userTitle.getId())
            .titleId(title.getId())
            .name(title.getName())
            .displayName(title.getDisplayName())
            .rarity(title.getRarity().name())
            .colorCode(title.getColorCode())
            .iconUrl(title.getIconUrl())
            .build();
    }

    private PublicProfileResponse.EquippedTitleInfo toPublicEquippedTitleInfo(UserTitle userTitle) {
        Title title = userTitle.getTitle();
        return PublicProfileResponse.EquippedTitleInfo.builder()
            .titleId(title.getId())
            .name(title.getName())
            .displayName(title.getDisplayName())
            .rarity(title.getRarity().name())
            .colorCode(title.getColorCode())
            .iconUrl(title.getIconUrl())
            .build();
    }

    private PublicProfileResponse.GuildInfo toGuildInfo(GuildMember guildMember) {
        Guild guild = guildMember.getGuild();
        long memberCount = guildMemberRepository.countActiveMembers(guild.getId());
        return PublicProfileResponse.GuildInfo.builder()
            .guildId(guild.getId())
            .name(guild.getName())
            .imageUrl(guild.getImageUrl())
            .level(guild.getCurrentLevel())
            .memberCount((int) memberCount)
            .build();
    }

    private UserTitleItem toUserTitleItem(UserTitle userTitle) {
        Title title = userTitle.getTitle();
        return UserTitleItem.builder()
            .userTitleId(userTitle.getId())
            .titleId(title.getId())
            .name(title.getName())
            .displayName(title.getDisplayName())
            .description(title.getDescription())
            .rarity(title.getRarity().name())
            .positionType(title.getPositionType().name())
            .colorCode(title.getColorCode())
            .iconUrl(title.getIconUrl())
            .isEquipped(userTitle.getIsEquipped())
            .equippedPosition(userTitle.getEquippedPosition() != null
                ? userTitle.getEquippedPosition().name()
                : null)
            .acquiredAt(userTitle.getAcquiredAt())
            .build();
    }

    private Integer getNextLevelRequiredExp(int currentLevel) {
        return levelConfigRepository.findByLevel(currentLevel)
            .map(LevelConfig::getRequiredExp)
            .orElse(100 + (currentLevel - 1) * 50);  // 기본 공식
    }

    private Double calculateRankingPercentile(long rankingPoints) {
        long totalUsers = userStatsRepository.countTotalUsers();
        if (totalUsers == 0) {
            return 100.0;  // 사용자가 없으면 상위 100%
        }

        long rank = userStatsRepository.calculateRank(rankingPoints);
        // 상위 X% = (순위 / 전체 사용자 수) * 100
        return Math.round((double) rank / totalUsers * 1000) / 10.0;
    }

    /**
     * 두 등급 중 더 높은 등급 반환
     */
    private TitleRarity getHighestRarity(TitleRarity r1, TitleRarity r2) {
        if (r1 == null) return r2;
        if (r2 == null) return r1;
        return r1.ordinal() > r2.ordinal() ? r1 : r2;
    }
}
