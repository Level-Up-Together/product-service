package io.pinkspider.leveluptogethermvp.userservice.unit.user.api;

import io.pinkspider.global.api.ApiResult;
import io.pinkspider.leveluptogethermvp.userservice.unit.user.application.UserAdminInternalService;
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
import io.pinkspider.leveluptogethermvp.userservice.unit.user.domain.dto.admin.UserTitleAdminResponse;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/internal/users")
@RequiredArgsConstructor
public class UserAdminInternalController {

    private final UserAdminInternalService userAdminInternalService;

    @GetMapping
    public ApiResult<UserAdminPageResponse> searchUsers(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String provider,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "20") int size,
            @RequestParam(required = false, defaultValue = "createdAt") String sortBy,
            @RequestParam(required = false, defaultValue = "DESC") String sortDirection) {
        return ApiResult.<UserAdminPageResponse>builder()
            .value(userAdminInternalService.searchUsers(keyword, provider, page, size, sortBy, sortDirection))
            .build();
    }

    @GetMapping("/{userId}")
    public ApiResult<UserAdminResponse> getUser(@PathVariable String userId) {
        return ApiResult.<UserAdminResponse>builder()
            .value(userAdminInternalService.getUser(userId))
            .build();
    }

    @GetMapping("/email/{email}")
    public ApiResult<UserAdminResponse> getUserByEmail(@PathVariable String email) {
        return ApiResult.<UserAdminResponse>builder()
            .value(userAdminInternalService.getUserByEmail(email))
            .build();
    }

    @GetMapping("/statistics")
    public ApiResult<UserStatisticsAdminResponse> getStatistics() {
        return ApiResult.<UserStatisticsAdminResponse>builder()
            .value(userAdminInternalService.getStatistics())
            .build();
    }

    @GetMapping("/{userId}/detail")
    public ApiResult<UserDetailAdminResponse> getUserDetail(@PathVariable String userId) {
        return ApiResult.<UserDetailAdminResponse>builder()
            .value(userAdminInternalService.getUserDetail(userId))
            .build();
    }

    @GetMapping("/{userId}/titles")
    public ApiResult<List<UserTitleAdminResponse>> getUserTitles(@PathVariable String userId) {
        return ApiResult.<List<UserTitleAdminResponse>>builder()
            .value(userAdminInternalService.getUserTitles(userId))
            .build();
    }

    @GetMapping("/{userId}/achievements")
    public ApiResult<List<UserAchievementAdminResponse>> getUserAchievements(@PathVariable String userId) {
        return ApiResult.<List<UserAchievementAdminResponse>>builder()
            .value(userAdminInternalService.getUserAchievements(userId))
            .build();
    }

    @GetMapping("/{userId}/guild")
    public ApiResult<UserGuildInfoAdminResponse> getUserGuildInfo(@PathVariable String userId) {
        return ApiResult.<UserGuildInfoAdminResponse>builder()
            .value(userAdminInternalService.getUserGuildInfo(userId))
            .build();
    }

    @DeleteMapping("/{userId}/profile-image")
    public ApiResult<UserAdminResponse> resetProfileImage(
            @PathVariable String userId,
            @RequestParam(required = false) String reason) {
        return ApiResult.<UserAdminResponse>builder()
            .value(userAdminInternalService.resetProfileImage(userId, reason))
            .build();
    }

    @PostMapping("/{userId}/blacklist")
    public ApiResult<UserBlacklistAdminResponse> addToBlacklist(
            @PathVariable String userId,
            @RequestBody UserBlacklistAdminRequest request) {
        return ApiResult.<UserBlacklistAdminResponse>builder()
            .value(userAdminInternalService.addToBlacklist(userId, request))
            .build();
    }

    @DeleteMapping("/{userId}/blacklist")
    public ApiResult<Void> removeFromBlacklist(
            @PathVariable String userId,
            @RequestParam Long adminId,
            @RequestParam(required = false) String reason) {
        userAdminInternalService.removeFromBlacklist(userId, adminId, reason);
        return ApiResult.<Void>builder().build();
    }

    @GetMapping("/{userId}/blacklist/history")
    public ApiResult<List<UserBlacklistAdminResponse>> getBlacklistHistory(@PathVariable String userId) {
        return ApiResult.<List<UserBlacklistAdminResponse>>builder()
            .value(userAdminInternalService.getBlacklistHistory(userId))
            .build();
    }

    @GetMapping("/blacklist")
    public ApiResult<UserBlacklistPageAdminResponse> getBlacklistList(
            @RequestParam(required = false) String blacklistType,
            @RequestParam(required = false) Boolean activeOnly,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "20") int size) {
        return ApiResult.<UserBlacklistPageAdminResponse>builder()
            .value(userAdminInternalService.getBlacklistList(blacklistType, activeOnly, startDate, endDate, page, size))
            .build();
    }

    @PostMapping("/batch")
    public ApiResult<Map<String, UserBriefAdminResponse>> getUsersByIds(@RequestBody List<String> userIds) {
        return ApiResult.<Map<String, UserBriefAdminResponse>>builder()
            .value(userAdminInternalService.getUsersByIds(userIds))
            .build();
    }
}
