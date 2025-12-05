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
}

