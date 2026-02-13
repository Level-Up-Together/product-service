package io.pinkspider.leveluptogethermvp.userservice.unit.user.application;

import io.pinkspider.global.exception.CustomException;
import io.pinkspider.global.util.CryptoUtils;
import io.pinkspider.leveluptogethermvp.gamificationservice.application.GamificationQueryFacadeService;
import io.pinkspider.leveluptogethermvp.gamificationservice.achievement.domain.dto.UserAchievementResponse;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.entity.UserTitle;
import io.pinkspider.leveluptogethermvp.guildservice.application.GuildQueryFacadeService;
import io.pinkspider.leveluptogethermvp.guildservice.domain.dto.GuildFacadeDto.UserGuildAdminInfo;
import io.pinkspider.leveluptogethermvp.userservice.unit.user.domain.dto.admin.BlacklistListItemAdminResponse;
import io.pinkspider.leveluptogethermvp.userservice.unit.user.domain.dto.admin.UserAchievementAdminResponse;
import io.pinkspider.leveluptogethermvp.userservice.unit.user.domain.dto.admin.UserAdminPageResponse;
import io.pinkspider.leveluptogethermvp.userservice.unit.user.domain.dto.admin.UserAdminResponse;
import io.pinkspider.leveluptogethermvp.userservice.unit.user.domain.dto.admin.UserBlacklistAdminRequest;
import io.pinkspider.leveluptogethermvp.userservice.unit.user.domain.dto.admin.UserBlacklistAdminResponse;
import io.pinkspider.leveluptogethermvp.userservice.unit.user.domain.dto.admin.UserBlacklistPageAdminResponse;
import io.pinkspider.leveluptogethermvp.userservice.unit.user.domain.dto.admin.UserBriefAdminResponse;
import io.pinkspider.leveluptogethermvp.userservice.unit.user.domain.dto.admin.UserDetailAdminResponse;
import io.pinkspider.leveluptogethermvp.userservice.unit.user.domain.dto.admin.UserGuildInfoAdminResponse;
import io.pinkspider.leveluptogethermvp.userservice.unit.user.domain.dto.admin.UserStatisticsAdminResponse;
import io.pinkspider.leveluptogethermvp.userservice.unit.user.domain.dto.admin.UserStatisticsAdminResponse.DailyCountDto;
import io.pinkspider.leveluptogethermvp.userservice.unit.user.domain.dto.admin.UserTitleAdminResponse;
import io.pinkspider.leveluptogethermvp.userservice.unit.user.domain.entity.UserBlacklist;
import io.pinkspider.leveluptogethermvp.userservice.unit.user.domain.entity.Users;
import io.pinkspider.leveluptogethermvp.userservice.unit.user.domain.enums.BlacklistType;
import io.pinkspider.leveluptogethermvp.userservice.unit.user.domain.enums.UserStatus;
import io.pinkspider.leveluptogethermvp.userservice.unit.user.infrastructure.UserBlacklistRepository;
import io.pinkspider.leveluptogethermvp.userservice.unit.user.infrastructure.UserRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserAdminInternalService {

    private final UserRepository userRepository;
    private final UserBlacklistRepository userBlacklistRepository;
    private final GamificationQueryFacadeService gamificationQueryFacadeService;
    private final GuildQueryFacadeService guildQueryFacadeService;

    // ========== 유저 검색/조회 ==========

    @Transactional(readOnly = true, transactionManager = "userTransactionManager")
    public UserAdminPageResponse searchUsers(String keyword, String provider,
                                              int page, int size, String sortBy, String sortDirection) {
        Sort sort = Sort.by(
            Sort.Direction.fromString(sortDirection != null ? sortDirection : "DESC"),
            sortBy != null ? sortBy : "createdAt"
        );
        Pageable pageable = PageRequest.of(page, size, sort);

        Page<Users> userPage = userRepository.searchUsersForAdmin(keyword, provider, pageable);
        Page<UserAdminResponse> responsePage = userPage.map(UserAdminResponse::from);
        return UserAdminPageResponse.from(responsePage);
    }

    @Transactional(readOnly = true, transactionManager = "userTransactionManager")
    public UserAdminResponse getUser(String userId) {
        Users user = userRepository.findById(userId)
            .orElseThrow(() -> new CustomException("404", "사용자를 찾을 수 없습니다."));
        return UserAdminResponse.from(user);
    }

    @Transactional(readOnly = true, transactionManager = "userTransactionManager")
    public UserAdminResponse getUserByEmail(String email) {
        String encryptedEmail = CryptoUtils.encryptAes(email);
        Users user = userRepository.findByEncryptedEmail(encryptedEmail)
            .orElseThrow(() -> new CustomException("404", "사용자를 찾을 수 없습니다."));
        return UserAdminResponse.from(user);
    }

    // ========== 유저 상세 (enriched) ==========

    @Transactional(readOnly = true, transactionManager = "userTransactionManager")
    public UserDetailAdminResponse getUserDetail(String userId) {
        Users user = userRepository.findById(userId)
            .orElseThrow(() -> new CustomException("404", "사용자를 찾을 수 없습니다."));

        List<UserTitleAdminResponse> titles = buildTitleResponses(userId);
        List<UserAchievementAdminResponse> achievements = buildAchievementResponses(userId);
        List<UserBlacklistAdminResponse> blacklistHistory = userBlacklistRepository
            .findAllByUserIdOrderByCreatedAtDesc(userId).stream()
            .map(UserBlacklistAdminResponse::from)
            .toList();

        UserBlacklistAdminResponse activeBlacklist = blacklistHistory.stream()
            .filter(b -> Boolean.TRUE.equals(b.isActive()))
            .findFirst()
            .orElse(null);

        return UserDetailAdminResponse.builder()
            .id(user.getId())
            .nickname(user.getNickname())
            .email(user.getEmail())
            .picture(user.getPicture())
            .provider(user.getProvider())
            .status(user.getStatus() != null ? user.getStatus().name() : null)
            .lastLoginIp(user.getLastLoginIp())
            .lastLoginCountry(user.getLastLoginCountry())
            .lastLoginCountryCode(user.getLastLoginCountryCode())
            .lastLoginAt(user.getLastLoginAt())
            .createdAt(user.getCreatedAt())
            .modifiedAt(user.getModifiedAt())
            .titles(titles)
            .achievements(achievements)
            .blacklistHistory(blacklistHistory)
            .activeBlacklist(activeBlacklist)
            .build();
    }

    // ========== 칭호/업적/길드 개별 조회 ==========

    public List<UserTitleAdminResponse> getUserTitles(String userId) {
        if (!userRepository.existsById(userId)) {
            throw new CustomException("404", "사용자를 찾을 수 없습니다.");
        }
        return buildTitleResponses(userId);
    }

    public List<UserAchievementAdminResponse> getUserAchievements(String userId) {
        if (!userRepository.existsById(userId)) {
            throw new CustomException("404", "사용자를 찾을 수 없습니다.");
        }
        return buildAchievementResponses(userId);
    }

    public UserGuildInfoAdminResponse getUserGuildInfo(String userId) {
        UserGuildAdminInfo info = guildQueryFacadeService.getUserGuildInfoForAdmin(userId);
        if (info == null) {
            return null;
        }
        return UserGuildInfoAdminResponse.builder()
            .guildId(info.guildId())
            .guildName(info.guildName())
            .guildImageUrl(info.guildImageUrl())
            .guildLevel(info.guildLevel())
            .role(info.role())
            .joinedAt(info.joinedAt())
            .memberCount(info.memberCount())
            .maxMembers(info.maxMembers())
            .build();
    }

    // ========== 통계 ==========

    @Transactional(readOnly = true, transactionManager = "userTransactionManager")
    public UserStatisticsAdminResponse getStatistics() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startOfToday = LocalDate.now().atStartOfDay();
        LocalDateTime startOfWeek = LocalDate.now().minusDays(7).atStartOfDay();
        LocalDateTime startOfMonth = LocalDate.now().minusDays(30).atStartOfDay();

        long totalUsers = userRepository.count();
        long newUsersToday = userRepository.countNewUsersSince(startOfToday);
        long newUsersThisWeek = userRepository.countNewUsersSince(startOfWeek);
        long newUsersThisMonth = userRepository.countNewUsersSince(startOfMonth);

        Map<String, Long> usersByProvider = new LinkedHashMap<>();
        for (Object[] row : userRepository.countUsersByProvider()) {
            usersByProvider.put(row[0].toString(), (Long) row[1]);
        }

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        List<DailyCountDto> dailyNewUsers = userRepository.countDailyNewUsers(startOfMonth, now).stream()
            .map(row -> DailyCountDto.builder()
                .date(row[0] instanceof LocalDate ? ((LocalDate) row[0]).format(formatter) : row[0].toString())
                .count((Long) row[1])
                .build())
            .toList();

        return UserStatisticsAdminResponse.builder()
            .totalUsers(totalUsers)
            .newUsersToday(newUsersToday)
            .newUsersThisWeek(newUsersThisWeek)
            .newUsersThisMonth(newUsersThisMonth)
            .usersByProvider(usersByProvider)
            .dailyNewUsers(dailyNewUsers)
            .build();
    }

    // ========== 프로필 초기화 ==========

    @Transactional(transactionManager = "userTransactionManager")
    public UserAdminResponse resetProfileImage(String userId, String reason) {
        Users user = userRepository.findById(userId)
            .orElseThrow(() -> new CustomException("404", "사용자를 찾을 수 없습니다."));

        String previousPicture = user.getPicture();
        user.updatePicture(null);
        userRepository.save(user);

        log.info("프로필 이미지 초기화 - userId: {}, previousPicture: {}, reason: {}",
            userId, previousPicture, reason);
        return UserAdminResponse.from(user);
    }

    // ========== 블랙리스트 ==========

    @Transactional(transactionManager = "userTransactionManager")
    public UserBlacklistAdminResponse addToBlacklist(String userId, UserBlacklistAdminRequest request) {
        Users user = userRepository.findById(userId)
            .orElseThrow(() -> new CustomException("404", "사용자를 찾을 수 없습니다."));

        BlacklistType type = BlacklistType.valueOf(request.blacklistType());

        if (type == BlacklistType.SUSPENSION && request.endedAt() == null) {
            throw new CustomException("400", "기간 정지의 경우 종료 일시가 필요합니다.");
        }
        if (type == BlacklistType.SUSPENSION && request.endedAt() != null
            && request.endedAt().isBefore(LocalDateTime.now())) {
            throw new CustomException("400", "종료 일시는 현재보다 미래여야 합니다.");
        }

        userBlacklistRepository.deactivateAllByUserId(userId);

        UserBlacklist blacklist = UserBlacklist.builder()
            .userId(userId)
            .blacklistType(type)
            .reason(request.reason())
            .adminId(request.adminId())
            .startedAt(LocalDateTime.now())
            .endedAt(type == BlacklistType.SUSPENSION ? request.endedAt() : null)
            .isActive(true)
            .build();
        userBlacklistRepository.save(blacklist);

        user.updateStatus(type == BlacklistType.PERMANENT_BAN
            ? UserStatus.PERMANENTLY_BANNED
            : UserStatus.SUSPENDED);
        userRepository.save(user);

        log.info("사용자 블랙리스트 추가 - userId: {}, type: {}, reason: {}, adminId: {}",
            userId, type, request.reason(), request.adminId());
        return UserBlacklistAdminResponse.from(blacklist);
    }

    @Transactional(transactionManager = "userTransactionManager")
    public void removeFromBlacklist(String userId, Long adminId, String reason) {
        Users user = userRepository.findById(userId)
            .orElseThrow(() -> new CustomException("404", "사용자를 찾을 수 없습니다."));

        int deactivated = userBlacklistRepository.deactivateAllByUserId(userId);
        if (deactivated == 0) {
            throw new CustomException("400", "활성화된 블랙리스트가 없습니다.");
        }

        user.updateStatus(UserStatus.ACTIVE);
        userRepository.save(user);

        log.info("사용자 블랙리스트 해제 - userId: {}, adminId: {}, reason: {}", userId, adminId, reason);
    }

    @Transactional(readOnly = true, transactionManager = "userTransactionManager")
    public List<UserBlacklistAdminResponse> getBlacklistHistory(String userId) {
        if (!userRepository.existsById(userId)) {
            throw new CustomException("404", "사용자를 찾을 수 없습니다.");
        }
        return userBlacklistRepository.findAllByUserIdOrderByCreatedAtDesc(userId).stream()
            .map(UserBlacklistAdminResponse::from)
            .toList();
    }

    @Transactional(readOnly = true, transactionManager = "userTransactionManager")
    public UserBlacklistPageAdminResponse getBlacklistList(
            String blacklistType, Boolean activeOnly,
            LocalDateTime startDate, LocalDateTime endDate,
            int page, int size) {

        Pageable pageable = PageRequest.of(page, size);
        BlacklistType type = blacklistType != null ? BlacklistType.valueOf(blacklistType) : null;
        boolean hasDateFilter = startDate != null && endDate != null;

        Page<UserBlacklist> blacklistPage;

        if (Boolean.TRUE.equals(activeOnly)) {
            if (hasDateFilter) {
                blacklistPage = type != null
                    ? userBlacklistRepository.findByStartedAtBetweenAndBlacklistTypeAndIsActiveTrue(startDate, endDate, type, pageable)
                    : userBlacklistRepository.findByStartedAtBetweenAndIsActiveTrue(startDate, endDate, pageable);
            } else {
                blacklistPage = type != null
                    ? userBlacklistRepository.findAllByIsActiveTrueAndBlacklistTypeOrderByCreatedAtDesc(type, pageable)
                    : userBlacklistRepository.findAllByIsActiveTrueOrderByCreatedAtDesc(pageable);
            }
        } else {
            blacklistPage = hasDateFilter
                ? userBlacklistRepository.findByStartedAtBetween(startDate, endDate, pageable)
                : userBlacklistRepository.findAllByOrderByCreatedAtDesc(pageable);
        }

        List<String> userIds = blacklistPage.getContent().stream()
            .map(UserBlacklist::getUserId).distinct().toList();
        Map<String, Users> userMap = userRepository.findAllByIdIn(userIds).stream()
            .collect(Collectors.toMap(Users::getId, u -> u));

        List<BlacklistListItemAdminResponse> content = blacklistPage.getContent().stream()
            .map(b -> BlacklistListItemAdminResponse.from(b, userMap.get(b.getUserId())))
            .toList();

        return UserBlacklistPageAdminResponse.builder()
            .content(content)
            .page(blacklistPage.getNumber())
            .size(blacklistPage.getSize())
            .totalElements(blacklistPage.getTotalElements())
            .totalPages(blacklistPage.getTotalPages())
            .first(blacklistPage.isFirst())
            .last(blacklistPage.isLast())
            .build();
    }

    // ========== 배치 유저 정보 ==========

    @Transactional(readOnly = true, transactionManager = "userTransactionManager")
    public Map<String, UserBriefAdminResponse> getUsersByIds(List<String> userIds) {
        return userRepository.findAllByIdIn(userIds).stream()
            .collect(Collectors.toMap(Users::getId, UserBriefAdminResponse::from));
    }

    // ========== private helpers ==========

    private List<UserTitleAdminResponse> buildTitleResponses(String userId) {
        List<UserTitle> titles = gamificationQueryFacadeService.getUserTitleEntitiesWithTitle(userId);
        return titles.stream()
            .map(ut -> UserTitleAdminResponse.builder()
                .id(ut.getId())
                .titleId(ut.getTitle() != null ? ut.getTitle().getId() : null)
                .titleName(ut.getTitle() != null ? ut.getTitle().getName() : null)
                .titleRarity(ut.getTitle() != null && ut.getTitle().getRarity() != null
                    ? ut.getTitle().getRarity().name() : null)
                .titlePositionType(ut.getTitle() != null && ut.getTitle().getPositionType() != null
                    ? ut.getTitle().getPositionType().name() : null)
                .titleColorCode(ut.getTitle() != null ? ut.getTitle().getColorCode() : null)
                .acquiredAt(ut.getAcquiredAt())
                .isEquipped(ut.getIsEquipped())
                .equippedPosition(ut.getEquippedPosition() != null
                    ? ut.getEquippedPosition().name() : null)
                .build())
            .toList();
    }

    private List<UserAchievementAdminResponse> buildAchievementResponses(String userId) {
        List<UserAchievementResponse> achievements = gamificationQueryFacadeService.getUserAchievements(userId);
        return achievements.stream()
            .map(ua -> UserAchievementAdminResponse.builder()
                .id(ua.getId())
                .achievementId(ua.getAchievementId())
                .achievementName(ua.getName())
                .achievementCategoryCode(ua.getCategoryCode())
                .achievementIconUrl(ua.getIconUrl())
                .currentCount(ua.getCurrentCount())
                .requiredCount(ua.getRequiredCount())
                .progressPercent(ua.getProgressPercent())
                .isCompleted(ua.getIsCompleted())
                .completedAt(ua.getCompletedAt())
                .isRewardClaimed(ua.getIsRewardClaimed())
                .build())
            .toList();
    }
}
