package io.pinkspider.global.component;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.pinkspider.global.constants.MetaServiceConstants;
import io.pinkspider.global.domain.dto.CommonCodeDto;
import io.pinkspider.global.exception.NoCommonCodeException;
import java.lang.reflect.Field;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
@DisplayName("CommonCodeHelper 단위 테스트")
class CommonCodeHelperTest {

    private RedisTemplate<String, Object> mockRedisTemplate;
    private ValueOperations<String, Object> mockValueOperations;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() throws Exception {
        mockRedisTemplate = mock(RedisTemplate.class);
        mockValueOperations = mock(ValueOperations.class);

        // static 필드 주입
        Field field = CommonCodeHelper.class.getDeclaredField("redisTemplateForObject");
        field.setAccessible(true);
        field.set(null, mockRedisTemplate);

        // MetaServiceConstants.COMMON_CODE 설정
        Field commonCodeField = MetaServiceConstants.class.getDeclaredField("COMMON_CODE");
        commonCodeField.setAccessible(true);
        commonCodeField.set(null, "common:codes");
    }

    @AfterEach
    void tearDown() throws Exception {
        // 테스트 후 static 필드 초기화
        Field field = CommonCodeHelper.class.getDeclaredField("redisTemplateForObject");
        field.setAccessible(true);
        field.set(null, null);
    }

    private List<CommonCodeDto> buildCommonCodes() {
        return List.of(
                CommonCodeDto.builder()
                        .id("CODE-001")
                        .codeName("코드명1")
                        .codeTitle("코드제목1")
                        .codeTitleEn("Code Title 1")
                        .parentId("PARENT-001")
                        .build(),
                CommonCodeDto.builder()
                        .id("CODE-002")
                        .codeName("코드명2")
                        .codeTitle("코드제목2")
                        .codeTitleEn("Code Title 2")
                        .parentId("PARENT-001")
                        .build(),
                CommonCodeDto.builder()
                        .id("CODE-003")
                        .codeName("코드명3")
                        .codeTitle("코드제목3")
                        .parentId("PARENT-002")
                        .build());
    }

    @Nested
    @DisplayName("getCommonCodeById 테스트")
    class GetCommonCodeByIdTest {

        @Test
        @DisplayName("ID로 공통코드를 조회한다")
        void getCommonCodeById_success() {
            // given
            List<CommonCodeDto> codes = buildCommonCodes();
            when(mockRedisTemplate.opsForValue()).thenReturn(mockValueOperations);
            when(mockValueOperations.get("common:codes")).thenReturn(codes);

            // when
            CommonCodeDto result = CommonCodeHelper.getCommonCodeById("CODE-001");

            // then
            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo("CODE-001");
            assertThat(result.getCodeName()).isEqualTo("코드명1");
            assertThat(result.getCodeTitle()).isEqualTo("코드제목1");
        }

        @Test
        @DisplayName("존재하지 않는 ID면 NoCommonCodeException을 던진다")
        void getCommonCodeById_notFound() {
            // given
            List<CommonCodeDto> codes = buildCommonCodes();
            when(mockRedisTemplate.opsForValue()).thenReturn(mockValueOperations);
            when(mockValueOperations.get("common:codes")).thenReturn(codes);

            // when & then
            assertThatThrownBy(() -> CommonCodeHelper.getCommonCodeById("NOT-EXIST"))
                    .isInstanceOf(NoCommonCodeException.class);
        }

        @Test
        @DisplayName("두 번째 코드도 정확히 조회한다")
        void getCommonCodeById_secondItem() {
            // given
            List<CommonCodeDto> codes = buildCommonCodes();
            when(mockRedisTemplate.opsForValue()).thenReturn(mockValueOperations);
            when(mockValueOperations.get("common:codes")).thenReturn(codes);

            // when
            CommonCodeDto result = CommonCodeHelper.getCommonCodeById("CODE-002");

            // then
            assertThat(result.getId()).isEqualTo("CODE-002");
            assertThat(result.getCodeTitle()).isEqualTo("코드제목2");
        }
    }

    @Nested
    @DisplayName("getChildCommonCodeByParentId 테스트")
    class GetChildCommonCodeByParentIdTest {

        @Test
        @DisplayName("부모 ID로 자식 공통코드 목록을 반환한다")
        void getChildCommonCodeByParentId_success() {
            // given
            List<CommonCodeDto> codes = buildCommonCodes();
            when(mockRedisTemplate.opsForValue()).thenReturn(mockValueOperations);
            when(mockValueOperations.get("common:codes")).thenReturn(codes);

            // when
            List<CommonCodeDto> result =
                    CommonCodeHelper.getChildCommonCodeByParentId("PARENT-001");

            // then
            assertThat(result).hasSize(2);
            assertThat(result)
                    .extracting(CommonCodeDto::getId)
                    .containsExactlyInAnyOrder("CODE-001", "CODE-002");
        }

        @Test
        @DisplayName("자식이 없는 부모 ID면 빈 목록을 반환한다")
        void getChildCommonCodeByParentId_noChildren() {
            // given
            List<CommonCodeDto> codes = buildCommonCodes();
            when(mockRedisTemplate.opsForValue()).thenReturn(mockValueOperations);
            when(mockValueOperations.get("common:codes")).thenReturn(codes);

            // when
            List<CommonCodeDto> result =
                    CommonCodeHelper.getChildCommonCodeByParentId("NOT-EXIST-PARENT");

            // then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("다른 부모 ID의 자식도 올바르게 필터링된다")
        void getChildCommonCodeByParentId_differentParent() {
            // given
            List<CommonCodeDto> codes = buildCommonCodes();
            when(mockRedisTemplate.opsForValue()).thenReturn(mockValueOperations);
            when(mockValueOperations.get("common:codes")).thenReturn(codes);

            // when
            List<CommonCodeDto> result =
                    CommonCodeHelper.getChildCommonCodeByParentId("PARENT-002");

            // then
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getId()).isEqualTo("CODE-003");
        }
    }

    @Nested
    @DisplayName("getCodeTitleById 테스트")
    class GetCodeTitleByIdTest {

        @Test
        @DisplayName("ID로 코드 제목을 반환한다")
        void getCodeTitleById_success() {
            // given
            List<CommonCodeDto> codes = buildCommonCodes();
            when(mockRedisTemplate.opsForValue()).thenReturn(mockValueOperations);
            when(mockValueOperations.get("common:codes")).thenReturn(codes);

            // when
            String result = CommonCodeHelper.getCodeTitleById("CODE-001");

            // then
            assertThat(result).isEqualTo("코드제목1");
        }

        @Test
        @DisplayName("존재하지 않는 ID면 NoCommonCodeException을 던진다")
        void getCodeTitleById_notFound() {
            // given
            List<CommonCodeDto> codes = buildCommonCodes();
            when(mockRedisTemplate.opsForValue()).thenReturn(mockValueOperations);
            when(mockValueOperations.get("common:codes")).thenReturn(codes);

            // when & then
            assertThatThrownBy(() -> CommonCodeHelper.getCodeTitleById("INVALID"))
                    .isInstanceOf(NoCommonCodeException.class);
        }
    }

    @Nested
    @DisplayName("getCodeNameById 테스트")
    class GetCodeNameByIdTest {

        @Test
        @DisplayName("ID로 코드명을 반환한다")
        void getCodeNameById_success() {
            // given
            List<CommonCodeDto> codes = buildCommonCodes();
            when(mockRedisTemplate.opsForValue()).thenReturn(mockValueOperations);
            when(mockValueOperations.get("common:codes")).thenReturn(codes);

            // when
            String result = CommonCodeHelper.getCodeNameById("CODE-002");

            // then
            assertThat(result).isEqualTo("코드명2");
        }

        @Test
        @DisplayName("존재하지 않는 ID면 NoCommonCodeException을 던진다")
        void getCodeNameById_notFound() {
            // given
            List<CommonCodeDto> codes = buildCommonCodes();
            when(mockRedisTemplate.opsForValue()).thenReturn(mockValueOperations);
            when(mockValueOperations.get("common:codes")).thenReturn(codes);

            // when & then
            assertThatThrownBy(() -> CommonCodeHelper.getCodeNameById("INVALID"))
                    .isInstanceOf(NoCommonCodeException.class);
        }
    }
}
