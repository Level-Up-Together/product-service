package io.pinkspider.leveluptogethermvp.metaservice.api;

import io.pinkspider.global.api.ApiResult;
import io.pinkspider.global.component.CommonCodeHelper;
import io.pinkspider.global.constants.msaapiuri.MetaServiceUriContants;
import io.pinkspider.global.domain.dto.CommonCodeDto;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class CommonCodeController {

    // 공통코드 조회의 경우, redis에 올린 데이터를 기반으로 response한다.
    @GetMapping(MetaServiceUriContants.COMMON_CODE_BY_PARENT_ID)
    public ApiResult<List<CommonCodeDto>> getChildCommonCodeByParentId(@PathVariable("parent-id") String parentId) {

        List<CommonCodeDto> commonCodeDtoList = CommonCodeHelper.getChildCommonCodeByParentId(parentId);

        return ApiResult.<List<CommonCodeDto>>builder()
            .value(commonCodeDtoList)
            .build();
    }

    @GetMapping(MetaServiceUriContants.COMMON_CODE_BY_ID)
    public ApiResult<CommonCodeDto> getCommonCodeById(@PathVariable("id") String id) {

        CommonCodeDto commonCodeDto = CommonCodeHelper.getCommonCodeById(id);

        return ApiResult.<CommonCodeDto>builder()
            .value(commonCodeDto)
            .build();
    }

    // TODO	-> insert commonCode admin 개발시 진행
    //  공통코드를 입력/수정 즉시 redis의 내용을 업데이트할지 별로 이벤트르 줄지는 추후 결정
    @PostMapping(MetaServiceUriContants.COMMON_CODE)
    public ApiResult<?> createCommonCode() {
        return ApiResult.getBase();
    }

    // TODO	-> update commonCode admin 개발시 진행
    @PutMapping(MetaServiceUriContants.COMMON_CODE)
    public ApiResult<?> modifyCommonCode() {
        return ApiResult.getBase();
    }

    // TODO	-> delete commonCode
    @DeleteMapping(MetaServiceUriContants.COMMON_CODE)
    public ApiResult<?> deleteCommonCode() {
        return ApiResult.getBase();
    }
}
