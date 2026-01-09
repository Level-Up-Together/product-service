package io.pinkspider.leveluptogethermvp.metaservice.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import io.pinkspider.global.domain.dto.CommonCodeDto;
import io.pinkspider.leveluptogethermvp.metaservice.domain.entity.CommonCode;
import io.pinkspider.leveluptogethermvp.metaservice.infrastructure.CommonCodeRepository;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.modelmapper.ModelMapper;

@ExtendWith(MockitoExtension.class)
class CommonCodeServiceTest {

    @Mock
    private CommonCodeRepository commonCodeRepository;

    @Mock
    private ModelMapper modelMapper;

    @InjectMocks
    private CommonCodeService commonCodeService;

    private CommonCode createTestCommonCode(String id, String codeName, String codeTitle) {
        return CommonCode.builder()
            .id(id)
            .codeName(codeName)
            .codeTitle(codeTitle)
            .description("테스트 설명")
            .parentId(null)
            .build();
    }

    private CommonCodeDto createTestCommonCodeDto(String id, String codeName, String codeTitle) {
        CommonCodeDto dto = new CommonCodeDto();
        dto.setId(id);
        dto.setCodeName(codeName);
        dto.setCodeTitle(codeTitle);
        dto.setDescription("테스트 설명");
        dto.setParentId(null);
        return dto;
    }

    @Nested
    @DisplayName("retrieveAllCommonCode 테스트")
    class RetrieveAllCommonCodeTest {

        @Test
        @DisplayName("모든 공통 코드를 정상적으로 조회한다")
        void retrieveAllCommonCode_success() {
            // given
            CommonCode code1 = createTestCommonCode("M001", "MEMBER_STATUS", "회원 상태");
            CommonCode code2 = createTestCommonCode("M002", "MEMBER_GRADE", "회원 등급");
            List<CommonCode> commonCodeList = List.of(code1, code2);

            CommonCodeDto dto1 = createTestCommonCodeDto("M001", "MEMBER_STATUS", "회원 상태");
            CommonCodeDto dto2 = createTestCommonCodeDto("M002", "MEMBER_GRADE", "회원 등급");

            when(commonCodeRepository.retrieveAllCommonCode()).thenReturn(commonCodeList);
            when(modelMapper.map(code1, CommonCodeDto.class)).thenReturn(dto1);
            when(modelMapper.map(code2, CommonCodeDto.class)).thenReturn(dto2);

            // when
            List<CommonCodeDto> result = commonCodeService.retrieveAllCommonCode();

            // then
            assertThat(result).hasSize(2);
            assertThat(result.get(0).getId()).isEqualTo("M001");
            assertThat(result.get(0).getCodeName()).isEqualTo("MEMBER_STATUS");
            assertThat(result.get(1).getId()).isEqualTo("M002");
            assertThat(result.get(1).getCodeName()).isEqualTo("MEMBER_GRADE");
        }

        @Test
        @DisplayName("공통 코드가 없으면 빈 목록을 반환한다")
        void retrieveAllCommonCode_empty() {
            // given
            when(commonCodeRepository.retrieveAllCommonCode()).thenReturn(Collections.emptyList());

            // when
            List<CommonCodeDto> result = commonCodeService.retrieveAllCommonCode();

            // then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("단일 공통 코드를 조회한다")
        void retrieveAllCommonCode_singleItem() {
            // given
            CommonCode code = createTestCommonCode("A001", "ADMIN_ROLE", "관리자 역할");
            List<CommonCode> commonCodeList = List.of(code);

            CommonCodeDto dto = createTestCommonCodeDto("A001", "ADMIN_ROLE", "관리자 역할");

            when(commonCodeRepository.retrieveAllCommonCode()).thenReturn(commonCodeList);
            when(modelMapper.map(code, CommonCodeDto.class)).thenReturn(dto);

            // when
            List<CommonCodeDto> result = commonCodeService.retrieveAllCommonCode();

            // then
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getId()).isEqualTo("A001");
        }

        @Test
        @DisplayName("부모 ID가 있는 공통 코드를 조회한다")
        void retrieveAllCommonCode_withParentId() {
            // given
            CommonCode parentCode = CommonCode.builder()
                .id("M000")
                .codeName("MEMBER")
                .codeTitle("회원")
                .description("회원 관련 코드 그룹")
                .parentId(null)
                .build();

            CommonCode childCode = CommonCode.builder()
                .id("M001")
                .codeName("MEMBER_STATUS")
                .codeTitle("회원 상태")
                .description("회원 상태 코드")
                .parentId("M000")
                .build();

            List<CommonCode> commonCodeList = List.of(parentCode, childCode);

            CommonCodeDto parentDto = new CommonCodeDto();
            parentDto.setId("M000");
            parentDto.setCodeName("MEMBER");
            parentDto.setCodeTitle("회원");
            parentDto.setParentId(null);

            CommonCodeDto childDto = new CommonCodeDto();
            childDto.setId("M001");
            childDto.setCodeName("MEMBER_STATUS");
            childDto.setCodeTitle("회원 상태");
            childDto.setParentId("M000");

            when(commonCodeRepository.retrieveAllCommonCode()).thenReturn(commonCodeList);
            when(modelMapper.map(parentCode, CommonCodeDto.class)).thenReturn(parentDto);
            when(modelMapper.map(childCode, CommonCodeDto.class)).thenReturn(childDto);

            // when
            List<CommonCodeDto> result = commonCodeService.retrieveAllCommonCode();

            // then
            assertThat(result).hasSize(2);
            assertThat(result.get(0).getParentId()).isNull();
            assertThat(result.get(1).getParentId()).isEqualTo("M000");
        }
    }
}
