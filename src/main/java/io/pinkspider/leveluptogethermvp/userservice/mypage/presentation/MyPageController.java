package io.pinkspider.leveluptogethermvp.userservice.mypage.presentation;

import io.pinkspider.global.api.ApiResult;
import io.pinkspider.leveluptogethermvp.userservice.core.annotation.CurrentUser;
import io.pinkspider.leveluptogethermvp.userservice.mypage.application.MyPageService;
import io.pinkspider.leveluptogethermvp.userservice.mypage.domain.dto.MyPageResponse;
import io.pinkspider.leveluptogethermvp.userservice.mypage.domain.dto.MyPageResponse.ProfileInfo;
import io.pinkspider.leveluptogethermvp.userservice.mypage.domain.dto.ProfileUpdateRequest;
import io.pinkspider.leveluptogethermvp.userservice.mypage.domain.dto.TitleChangeRequest;
import io.pinkspider.leveluptogethermvp.userservice.mypage.domain.dto.TitleChangeResponse;
import io.pinkspider.leveluptogethermvp.userservice.mypage.domain.dto.UserTitleListResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/mypage")
@RequiredArgsConstructor
public class MyPageController {

    private final MyPageService myPageService;

    /**
     * MyPage 전체 데이터 조회
     *
     * @param userId 사용자 ID (JWT 토큰에서 추출)
     * @return MyPage 응답 (프로필, 경험치, 유저 정보)
     */
    @GetMapping
    public ResponseEntity<ApiResult<MyPageResponse>> getMyPage(
        @CurrentUser String userId) {

        MyPageResponse response = myPageService.getMyPage(userId);
        return ResponseEntity.ok(ApiResult.<MyPageResponse>builder().value(response).build());
    }

    /**
     * 프로필 이미지 변경
     *
     * @param userId 사용자 ID (JWT 토큰에서 추출)
     * @param request 프로필 업데이트 요청
     * @return 업데이트된 프로필 정보
     */
    @PutMapping("/profile")
    public ResponseEntity<ApiResult<ProfileInfo>> updateProfile(
        @CurrentUser String userId,
        @Valid @RequestBody ProfileUpdateRequest request) {

        ProfileInfo response = myPageService.updateProfileImage(userId, request);
        return ResponseEntity.ok(ApiResult.<ProfileInfo>builder().value(response).build());
    }

    /**
     * 보유 칭호 목록 조회
     *
     * @param userId 사용자 ID (JWT 토큰에서 추출)
     * @return 보유 칭호 목록
     */
    @GetMapping("/titles")
    public ResponseEntity<ApiResult<UserTitleListResponse>> getUserTitles(
        @CurrentUser String userId) {

        UserTitleListResponse response = myPageService.getUserTitles(userId);
        return ResponseEntity.ok(ApiResult.<UserTitleListResponse>builder().value(response).build());
    }

    /**
     * 칭호 변경 (좌측/우측 동시 설정)
     *
     * @param userId 사용자 ID (JWT 토큰에서 추출)
     * @param request 칭호 변경 요청 (좌측, 우측 필수)
     * @return 변경된 칭호 정보
     */
    @PutMapping("/titles")
    public ResponseEntity<ApiResult<TitleChangeResponse>> changeTitles(
        @CurrentUser String userId,
        @Valid @RequestBody TitleChangeRequest request) {

        TitleChangeResponse response = myPageService.changeTitles(userId, request);
        return ResponseEntity.ok(ApiResult.<TitleChangeResponse>builder().value(response).build());
    }
}
