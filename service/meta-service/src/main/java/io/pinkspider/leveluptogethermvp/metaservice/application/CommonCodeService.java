package io.pinkspider.leveluptogethermvp.metaservice.application;

import io.pinkspider.global.domain.dto.CommonCodeDto;
import io.pinkspider.leveluptogethermvp.metaservice.domain.entity.CommonCode;
import io.pinkspider.leveluptogethermvp.metaservice.infrastructure.CommonCodeRepository;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CommonCodeService {

    private final CommonCodeRepository commonCodeRepository;
    private final ModelMapper modelMapper;

    public List<CommonCodeDto> retrieveAllCommonCode() {
        List<CommonCode> commonCodeList = commonCodeRepository.retrieveAllCommonCode();

        return commonCodeList.stream()
            .map(commonCode -> modelMapper.map(commonCode, CommonCodeDto.class))
            .collect(Collectors.toList());
    }

    /**
     * 상위 그룹 ID로 하위 코드 목록 조회
     * @param parentId 상위 그룹 ID (예: "MS", "EX", "GR", "GS", "FS")
     * @return 하위 코드 목록
     */
    public List<CommonCodeDto> getCodesByParentId(String parentId) {
        List<CommonCode> codes = commonCodeRepository.findByParentId(parentId);
        return codes.stream()
            .map(code -> modelMapper.map(code, CommonCodeDto.class))
            .collect(Collectors.toList());
    }

    /**
     * 특정 코드의 로컬라이즈된 제목 조회
     * @param codeId 코드 ID
     * @param locale 언어 코드 (ko, en, ar)
     * @return 로컬라이즈된 제목
     */
    public String getLocalizedTitle(String codeId, String locale) {
        return commonCodeRepository.findById(codeId)
            .map(code -> {
                CommonCodeDto dto = modelMapper.map(code, CommonCodeDto.class);
                return dto.getLocalizedTitle(locale);
            })
            .orElse(null);
    }

    /**
     * 상위 그룹 목록 조회
     * @return 상위 그룹 코드 목록
     */
    public List<CommonCodeDto> getParentCodes() {
        List<CommonCode> codes = commonCodeRepository.findAllParentCodes();
        return codes.stream()
            .map(code -> modelMapper.map(code, CommonCodeDto.class))
            .collect(Collectors.toList());
    }
}

