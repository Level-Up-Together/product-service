package io.pinkspider.leveluptogethermvp.gamificationservice.experience.api;

import io.pinkspider.global.api.ApiResult;
import io.pinkspider.leveluptogethermvp.metaservice.userlevelconfig.domain.entity.UserLevelConfig;
import io.pinkspider.global.annotation.CurrentUser;
import io.pinkspider.leveluptogethermvp.gamificationservice.experience.application.UserExperienceService;
import io.pinkspider.leveluptogethermvp.gamificationservice.experience.domain.dto.UserExperienceResponse;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.entity.ExperienceHistory;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users/experience")
@RequiredArgsConstructor
public class UserExperienceController {

    private final UserExperienceService userExperienceService;

    /**
     * 내 경험치 정보 조회
     */
    @GetMapping("/me")
    public ResponseEntity<ApiResult<UserExperienceResponse>> getMyExperience(
        @CurrentUser String userId) {

        UserExperienceResponse response = userExperienceService.getUserExperience(userId);
        return ResponseEntity.ok(ApiResult.<UserExperienceResponse>builder().value(response).build());
    }

    /**
     * 내 경험치 획득 이력 조회
     */
    @GetMapping("/me/history")
    public ResponseEntity<ApiResult<Page<ExperienceHistory>>> getMyExperienceHistory(
        @CurrentUser String userId,
        @PageableDefault(size = 20) Pageable pageable) {

        Page<ExperienceHistory> history = userExperienceService.getExperienceHistory(userId, pageable);
        return ResponseEntity.ok(ApiResult.<Page<ExperienceHistory>>builder().value(history).build());
    }

    /**
     * 레벨별 필요 경험치 설정 조회
     */
    @GetMapping("/levels")
    public ResponseEntity<ApiResult<List<UserLevelConfig>>> getLevelConfigs() {
        List<UserLevelConfig> configs = userExperienceService.getAllLevelConfigs();
        return ResponseEntity.ok(ApiResult.<List<UserLevelConfig>>builder().value(configs).build());
    }

    /**
     * 레벨 설정 생성/수정 (Admin용)
     */
    @PostMapping("/levels")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResult<UserLevelConfig>> createOrUpdateLevelConfig(
        @RequestParam Integer level,
        @RequestParam Integer requiredExp,
        @RequestParam(required = false) Integer cumulativeExp) {

        UserLevelConfig config = userExperienceService.createOrUpdateLevelConfig(
            level, requiredExp, cumulativeExp);
        return ResponseEntity.ok(ApiResult.<UserLevelConfig>builder().value(config).build());
    }
}
