package io.pinkspider.leveluptogethermvp.userservice.terms.api;

import io.pinkspider.global.api.ApiResult;
import io.pinkspider.leveluptogethermvp.userservice.terms.application.UserTermsService;
import io.pinkspider.leveluptogethermvp.userservice.terms.domain.request.AgreementTermsByUserRequestDto;
import io.pinkspider.leveluptogethermvp.userservice.terms.domain.response.RecentTermsResponseDto;
import io.pinkspider.leveluptogethermvp.userservice.terms.domain.response.TermAgreementsByUserResponseDto;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/terms")
@CrossOrigin(origins = "*", maxAge = 3600)
@RequiredArgsConstructor
@Validated
public class UserTermsController {

    private final UserTermsService userTermsService;

    @GetMapping("/list")
    public ApiResult<List<RecentTermsResponseDto>> getRecentAllTerms() {

        List<RecentTermsResponseDto> termList = userTermsService.getRecentAllTerms();

        return ApiResult.<List<RecentTermsResponseDto>>builder()
            .value(termList)
            .build();
    }

    @GetMapping("/agreements/{userId}")
    public ApiResult<List<TermAgreementsByUserResponseDto>> getTermAgreementsByUser(@PathVariable("userId") String userId) {
        List<TermAgreementsByUserResponseDto> result = userTermsService.getTermAgreementsByUser(userId);

        return ApiResult.<List<TermAgreementsByUserResponseDto>>builder()
            .value(result)
            .build();
    }

    /**
     * 사용자가 동의하지 않은 약관 목록 조회
     * (약관 버전 업데이트 시 새로 동의가 필요한 약관만 반환)
     */
    @GetMapping("/pending/{userId}")
    public ApiResult<List<TermAgreementsByUserResponseDto>> getPendingTermsByUser(@PathVariable("userId") String userId) {
        List<TermAgreementsByUserResponseDto> result = userTermsService.getPendingTermsByUser(userId);

        return ApiResult.<List<TermAgreementsByUserResponseDto>>builder()
            .value(result)
            .build();
    }

    @PostMapping("/agreements/{userId}")
    public ApiResult<?> agreementTermsByUser(@PathVariable("userId") String userId, @RequestBody AgreementTermsByUserRequestDto requestDto) {
        userTermsService.agreementTermsByUser(userId, requestDto);
        return ApiResult.getBase();
    }
}
