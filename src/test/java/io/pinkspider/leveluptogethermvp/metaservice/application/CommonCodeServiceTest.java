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
import java.util.Optional;
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

    @Nested
    @DisplayName("getCodesByParentId 테스트")
    class GetCodesByParentIdTest {

        @Test
        @DisplayName("부모 ID로 하위 코드 목록을 조회한다")
        void getCodesByParentId_success() {
            // given
            String parentId = "MS";
            CommonCode code1 = CommonCode.builder()
                .id("MS01")
                .codeName("PENDING")
                .codeTitle("대기중")
                .codeTitleEn("Pending")
                .codeTitleAr("قيد الانتظار")
                .parentId(parentId)
                .build();
            CommonCode code2 = CommonCode.builder()
                .id("MS02")
                .codeName("IN_PROGRESS")
                .codeTitle("진행중")
                .codeTitleEn("In Progress")
                .codeTitleAr("قيد التنفيذ")
                .parentId(parentId)
                .build();

            CommonCodeDto dto1 = CommonCodeDto.builder()
                .id("MS01")
                .codeName("PENDING")
                .codeTitle("대기중")
                .codeTitleEn("Pending")
                .codeTitleAr("قيد الانتظار")
                .parentId(parentId)
                .build();
            CommonCodeDto dto2 = CommonCodeDto.builder()
                .id("MS02")
                .codeName("IN_PROGRESS")
                .codeTitle("진행중")
                .codeTitleEn("In Progress")
                .codeTitleAr("قيد التنفيذ")
                .parentId(parentId)
                .build();

            when(commonCodeRepository.findByParentId(parentId)).thenReturn(List.of(code1, code2));
            when(modelMapper.map(code1, CommonCodeDto.class)).thenReturn(dto1);
            when(modelMapper.map(code2, CommonCodeDto.class)).thenReturn(dto2);

            // when
            List<CommonCodeDto> result = commonCodeService.getCodesByParentId(parentId);

            // then
            assertThat(result).hasSize(2);
            assertThat(result.get(0).getId()).isEqualTo("MS01");
            assertThat(result.get(0).getCodeTitleEn()).isEqualTo("Pending");
            assertThat(result.get(1).getId()).isEqualTo("MS02");
        }

        @Test
        @DisplayName("존재하지 않는 부모 ID로 조회하면 빈 목록을 반환한다")
        void getCodesByParentId_notFound() {
            // given
            String parentId = "INVALID";
            when(commonCodeRepository.findByParentId(parentId)).thenReturn(Collections.emptyList());

            // when
            List<CommonCodeDto> result = commonCodeService.getCodesByParentId(parentId);

            // then
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("getLocalizedTitle 테스트")
    class GetLocalizedTitleTest {

        @Test
        @DisplayName("한국어 로케일로 제목을 조회한다")
        void getLocalizedTitle_korean() {
            // given
            String codeId = "MS01";
            CommonCode code = CommonCode.builder()
                .id(codeId)
                .codeTitle("대기중")
                .codeTitleEn("Pending")
                .codeTitleAr("قيد الانتظار")
                .build();
            CommonCodeDto dto = CommonCodeDto.builder()
                .id(codeId)
                .codeTitle("대기중")
                .codeTitleEn("Pending")
                .codeTitleAr("قيد الانتظار")
                .build();

            when(commonCodeRepository.findById(codeId)).thenReturn(Optional.of(code));
            when(modelMapper.map(code, CommonCodeDto.class)).thenReturn(dto);

            // when
            String result = commonCodeService.getLocalizedTitle(codeId, "ko");

            // then
            assertThat(result).isEqualTo("대기중");
        }

        @Test
        @DisplayName("영어 로케일로 제목을 조회한다")
        void getLocalizedTitle_english() {
            // given
            String codeId = "MS01";
            CommonCode code = CommonCode.builder()
                .id(codeId)
                .codeTitle("대기중")
                .codeTitleEn("Pending")
                .codeTitleAr("قيد الانتظار")
                .build();
            CommonCodeDto dto = CommonCodeDto.builder()
                .id(codeId)
                .codeTitle("대기중")
                .codeTitleEn("Pending")
                .codeTitleAr("قيد الانتظار")
                .build();

            when(commonCodeRepository.findById(codeId)).thenReturn(Optional.of(code));
            when(modelMapper.map(code, CommonCodeDto.class)).thenReturn(dto);

            // when
            String result = commonCodeService.getLocalizedTitle(codeId, "en");

            // then
            assertThat(result).isEqualTo("Pending");
        }

        @Test
        @DisplayName("아랍어 로케일로 제목을 조회한다")
        void getLocalizedTitle_arabic() {
            // given
            String codeId = "MS01";
            CommonCode code = CommonCode.builder()
                .id(codeId)
                .codeTitle("대기중")
                .codeTitleEn("Pending")
                .codeTitleAr("قيد الانتظار")
                .build();
            CommonCodeDto dto = CommonCodeDto.builder()
                .id(codeId)
                .codeTitle("대기중")
                .codeTitleEn("Pending")
                .codeTitleAr("قيد الانتظار")
                .build();

            when(commonCodeRepository.findById(codeId)).thenReturn(Optional.of(code));
            when(modelMapper.map(code, CommonCodeDto.class)).thenReturn(dto);

            // when
            String result = commonCodeService.getLocalizedTitle(codeId, "ar");

            // then
            assertThat(result).isEqualTo("قيد الانتظار");
        }

        @Test
        @DisplayName("존재하지 않는 코드 ID로 조회하면 null을 반환한다")
        void getLocalizedTitle_notFound() {
            // given
            String codeId = "INVALID";
            when(commonCodeRepository.findById(codeId)).thenReturn(Optional.empty());

            // when
            String result = commonCodeService.getLocalizedTitle(codeId, "ko");

            // then
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("영어 제목이 없으면 한국어 제목을 반환한다")
        void getLocalizedTitle_fallbackToKorean() {
            // given
            String codeId = "MS01";
            CommonCode code = CommonCode.builder()
                .id(codeId)
                .codeTitle("대기중")
                .codeTitleEn(null)
                .codeTitleAr(null)
                .build();
            CommonCodeDto dto = CommonCodeDto.builder()
                .id(codeId)
                .codeTitle("대기중")
                .codeTitleEn(null)
                .codeTitleAr(null)
                .build();

            when(commonCodeRepository.findById(codeId)).thenReturn(Optional.of(code));
            when(modelMapper.map(code, CommonCodeDto.class)).thenReturn(dto);

            // when
            String result = commonCodeService.getLocalizedTitle(codeId, "en");

            // then
            assertThat(result).isEqualTo("대기중");
        }
    }

    @Nested
    @DisplayName("getParentCodes 테스트")
    class GetParentCodesTest {

        @Test
        @DisplayName("상위 그룹 코드 목록을 조회한다")
        void getParentCodes_success() {
            // given
            CommonCode parent1 = CommonCode.builder()
                .id("MS")
                .codeName("MISSION_STATUS")
                .codeTitle("미션 상태")
                .codeTitleEn("Mission Status")
                .parentId(null)
                .build();
            CommonCode parent2 = CommonCode.builder()
                .id("EX")
                .codeName("EXECUTION_STATUS")
                .codeTitle("실행 상태")
                .codeTitleEn("Execution Status")
                .parentId(null)
                .build();

            CommonCodeDto dto1 = CommonCodeDto.builder()
                .id("MS")
                .codeName("MISSION_STATUS")
                .codeTitle("미션 상태")
                .codeTitleEn("Mission Status")
                .parentId(null)
                .build();
            CommonCodeDto dto2 = CommonCodeDto.builder()
                .id("EX")
                .codeName("EXECUTION_STATUS")
                .codeTitle("실행 상태")
                .codeTitleEn("Execution Status")
                .parentId(null)
                .build();

            when(commonCodeRepository.findAllParentCodes()).thenReturn(List.of(parent1, parent2));
            when(modelMapper.map(parent1, CommonCodeDto.class)).thenReturn(dto1);
            when(modelMapper.map(parent2, CommonCodeDto.class)).thenReturn(dto2);

            // when
            List<CommonCodeDto> result = commonCodeService.getParentCodes();

            // then
            assertThat(result).hasSize(2);
            assertThat(result.get(0).getId()).isEqualTo("MS");
            assertThat(result.get(0).getParentId()).isNull();
            assertThat(result.get(1).getId()).isEqualTo("EX");
            assertThat(result.get(1).getParentId()).isNull();
        }

        @Test
        @DisplayName("상위 그룹 코드가 없으면 빈 목록을 반환한다")
        void getParentCodes_empty() {
            // given
            when(commonCodeRepository.findAllParentCodes()).thenReturn(Collections.emptyList());

            // when
            List<CommonCodeDto> result = commonCodeService.getParentCodes();

            // then
            assertThat(result).isEmpty();
        }
    }
}
