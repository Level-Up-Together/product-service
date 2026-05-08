package io.pinkspider.leveluptogethermvp.userservice.core.converter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.pinkspider.leveluptogethermvp.userservice.core.enums.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("RoleConverter 테스트")
class RoleConverterTest {

    private RoleConverter roleConverter;

    @BeforeEach
    void setUp() {
        roleConverter = new RoleConverter();
    }

    @Nested
    @DisplayName("convertToDatabaseColumn 테스트")
    class ConvertToDatabaseColumnTest {

        @Test
        @DisplayName("ADMIN role을 DB 컬럼값으로 변환한다")
        void convertToDatabaseColumn_admin_returnsAdminWithCast() {
            // when
            Object result = roleConverter.convertToDatabaseColumn(Role.ADMIN);

            // then
            assertThat(result).isEqualTo("ADMIN::role");
        }

        @Test
        @DisplayName("USER role을 DB 컬럼값으로 변환한다")
        void convertToDatabaseColumn_user_returnsUserWithCast() {
            // when
            Object result = roleConverter.convertToDatabaseColumn(Role.USER);

            // then
            assertThat(result).isEqualTo("USER::role");
        }

        @Test
        @DisplayName("null 입력 시 null을 반환한다")
        void convertToDatabaseColumn_null_returnsNull() {
            // when
            Object result = roleConverter.convertToDatabaseColumn(null);

            // then
            assertThat(result).isNull();
        }
    }

    @Nested
    @DisplayName("convertToEntityAttribute 테스트")
    class ConvertToEntityAttributeTest {

        @Test
        @DisplayName("DB의 'ADMIN' 문자열을 Role.ADMIN으로 변환한다")
        void convertToEntityAttribute_adminString_returnsAdminRole() {
            // when
            Role result = roleConverter.convertToEntityAttribute("ADMIN");

            // then
            assertThat(result).isEqualTo(Role.ADMIN);
        }

        @Test
        @DisplayName("DB의 'USER' 문자열을 Role.USER로 변환한다")
        void convertToEntityAttribute_userString_returnsUserRole() {
            // when
            Role result = roleConverter.convertToEntityAttribute("USER");

            // then
            assertThat(result).isEqualTo(Role.USER);
        }

        @Test
        @DisplayName("null 입력 시 null을 반환한다")
        void convertToEntityAttribute_null_returnsNull() {
            // when
            Role result = roleConverter.convertToEntityAttribute(null);

            // then
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("알 수 없는 문자열 입력 시 IllegalArgumentException이 발생한다")
        void convertToEntityAttribute_unknownString_throwsIllegalArgumentException() {
            // when & then
            assertThatThrownBy(() -> roleConverter.convertToEntityAttribute("UNKNOWN_ROLE"))
                .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
