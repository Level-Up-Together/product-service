package io.pinkspider.leveluptogethermvp.userservice.mypage.presentation;

import io.pinkspider.global.api.ApiResult;
import io.pinkspider.leveluptogethermvp.userservice.core.annotation.CurrentUser;
import io.pinkspider.leveluptogethermvp.userservice.mypage.application.MyPageService;
import io.pinkspider.leveluptogethermvp.userservice.mypage.domain.dto.BioUpdateRequest;
import io.pinkspider.leveluptogethermvp.userservice.mypage.domain.dto.MyPageResponse;
import io.pinkspider.leveluptogethermvp.userservice.mypage.domain.dto.MyPageResponse.ProfileInfo;
import io.pinkspider.leveluptogethermvp.userservice.mypage.domain.dto.NicknameCheckResponse;
import io.pinkspider.leveluptogethermvp.userservice.mypage.domain.dto.NicknameStatusResponse;
import io.pinkspider.leveluptogethermvp.userservice.mypage.domain.dto.NicknameUpdateRequest;
import io.pinkspider.leveluptogethermvp.userservice.mypage.domain.dto.ProfileUpdateRequest;
import io.pinkspider.leveluptogethermvp.userservice.mypage.domain.dto.PublicProfileResponse;
import io.pinkspider.leveluptogethermvp.userservice.mypage.domain.dto.TitleChangeRequest;
import io.pinkspider.leveluptogethermvp.userservice.mypage.domain.dto.TitleChangeResponse;
import io.pinkspider.leveluptogethermvp.userservice.mypage.domain.dto.UserTitleListResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

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
     * 공개 프로필 조회 (타인의 프로필 조회 가능)
     *
     * @param targetUserId 조회할 사용자 ID
     * @param currentUserId 현재 로그인한 사용자 ID (선택적)
     * @return 공개 프로필 정보
     */
    @GetMapping("/profile/{userId}")
    public ResponseEntity<ApiResult<PublicProfileResponse>> getPublicProfile(
        @PathVariable("userId") String targetUserId,
        @CurrentUser(required = false) String currentUserId) {

        PublicProfileResponse response = myPageService.getPublicProfile(targetUserId, currentUserId);
        return ResponseEntity.ok(ApiResult.<PublicProfileResponse>builder().value(response).build());
    }

    /**
     * 자기소개 수정
     *
     * @param userId 사용자 ID (JWT 토큰에서 추출)
     * @param request 자기소개 수정 요청
     * @return 업데이트된 프로필 정보
     */
    @PutMapping("/bio")
    public ResponseEntity<ApiResult<ProfileInfo>> updateBio(
        @CurrentUser String userId,
        @Valid @RequestBody BioUpdateRequest request) {

        ProfileInfo response = myPageService.updateBio(userId, request.getBio());
        return ResponseEntity.ok(ApiResult.<ProfileInfo>builder().value(response).build());
    }

    /**
     * 프로필 이미지 변경 (URL 직접 지정)
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
     * 프로필 이미지 업로드
     *
     * @param userId 사용자 ID (JWT 토큰에서 추출)
     * @param image 업로드할 이미지 파일
     * @return 업데이트된 프로필 정보
     */
    @PostMapping(value = "/profile/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResult<ProfileInfo>> uploadProfileImage(
        @CurrentUser String userId,
        @RequestPart("image") MultipartFile image) {

        ProfileInfo response = myPageService.uploadProfileImage(userId, image);
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

    /**
     * 닉네임 중복 확인
     *
     * @param nickname 확인할 닉네임
     * @param userId 사용자 ID (수정 시 자신 제외, 선택적)
     * @return 사용 가능 여부
     */
    @GetMapping("/nickname/check")
    public ResponseEntity<ApiResult<NicknameCheckResponse>> checkNickname(
        @RequestParam String nickname,
        @CurrentUser(required = false) String userId) {

        boolean available = myPageService.isNicknameAvailable(nickname, userId);
        NicknameCheckResponse response = NicknameCheckResponse.builder()
            .nickname(nickname)
            .available(available)
            .message(available ? "사용 가능한 닉네임입니다." : "이미 사용 중인 닉네임입니다.")
            .build();

        return ResponseEntity.ok(ApiResult.<NicknameCheckResponse>builder().value(response).build());
    }

    /**
     * 닉네임 변경
     *
     * @param userId 사용자 ID (JWT 토큰에서 추출)
     * @param request 닉네임 변경 요청
     * @return 업데이트된 프로필 정보
     */
    @PutMapping("/nickname")
    public ResponseEntity<ApiResult<ProfileInfo>> updateNickname(
        @CurrentUser String userId,
        @Valid @RequestBody NicknameUpdateRequest request) {

        ProfileInfo response = myPageService.updateNickname(userId, request.getNickname());
        return ResponseEntity.ok(ApiResult.<ProfileInfo>builder().value(response).build());
    }

    /**
     * 닉네임 설정 필요 여부 확인
     * (OAuth 가입 시 닉네임 중복으로 자동 생성된 경우 true)
     *
     * @param userId 사용자 ID (JWT 토큰에서 추출)
     * @return 닉네임 설정 필요 여부
     */
    @GetMapping("/nickname/status")
    public ResponseEntity<ApiResult<NicknameStatusResponse>> getNicknameStatus(
        @CurrentUser String userId) {

        boolean needsSetup = myPageService.needsNicknameSetup(userId);
        NicknameStatusResponse response = NicknameStatusResponse.builder()
            .needsNicknameSetup(needsSetup)
            .build();

        return ResponseEntity.ok(ApiResult.<NicknameStatusResponse>builder().value(response).build());
    }
}
