package io.pinkspider.global.resolver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.pinkspider.global.annotation.CurrentUser;
import io.pinkspider.global.exception.CustomException;
import java.lang.reflect.Method;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

class CurrentUserArgumentResolverTest {

    private CurrentUserArgumentResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new CurrentUserArgumentResolver();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Nested
    @DisplayName("supportsParameter 테스트")
    class SupportsParameterTest {

        @Test
        @DisplayName("@CurrentUser 어노테이션과 String 타입이면 true를 반환한다")
        void supportsParameter_withCurrentUserAndString_returnsTrue() throws NoSuchMethodException {
            // given
            Method method = TestController.class.getMethod("testMethod", String.class);
            MethodParameter parameter = new MethodParameter(method, 0);

            // when
            boolean result = resolver.supportsParameter(parameter);

            // then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("@CurrentUser 어노테이션이 없으면 false를 반환한다")
        void supportsParameter_withoutCurrentUser_returnsFalse() throws NoSuchMethodException {
            // given
            Method method = TestController.class.getMethod("testMethodWithoutAnnotation", String.class);
            MethodParameter parameter = new MethodParameter(method, 0);

            // when
            boolean result = resolver.supportsParameter(parameter);

            // then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("String 타입이 아니면 false를 반환한다")
        void supportsParameter_withNonStringType_returnsFalse() throws NoSuchMethodException {
            // given
            Method method = TestController.class.getMethod("testMethodWithLong", Long.class);
            MethodParameter parameter = new MethodParameter(method, 0);

            // when
            boolean result = resolver.supportsParameter(parameter);

            // then
            assertThat(result).isFalse();
        }
    }

    @Nested
    @DisplayName("resolveArgument 테스트")
    class ResolveArgumentTest {

        @Test
        @DisplayName("인증된 사용자의 ID를 반환한다 (String principal)")
        void resolveArgument_withStringPrincipal_returnsUserId() throws Exception {
            // given
            String userId = "test-user-123";
            UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(userId, null, java.util.Collections.emptyList());
            SecurityContextHolder.getContext().setAuthentication(auth);

            Method method = TestController.class.getMethod("testMethod", String.class);
            MethodParameter parameter = new MethodParameter(method, 0);

            // when
            Object result = resolver.resolveArgument(parameter, null, null, null);

            // then
            assertThat(result).isEqualTo(userId);
        }

        @Test
        @DisplayName("인증된 사용자의 ID를 반환한다 (UserDetails principal)")
        void resolveArgument_withUserDetailsPrincipal_returnsUserId() throws Exception {
            // given
            String userId = "test-user-123";
            UserDetails userDetails = User.withUsername(userId)
                .password("password")
                .authorities("ROLE_USER")
                .build();
            UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
            SecurityContextHolder.getContext().setAuthentication(auth);

            Method method = TestController.class.getMethod("testMethod", String.class);
            MethodParameter parameter = new MethodParameter(method, 0);

            // when
            Object result = resolver.resolveArgument(parameter, null, null, null);

            // then
            assertThat(result).isEqualTo(userId);
        }

        @Test
        @DisplayName("인증되지 않은 경우 필수이면 예외가 발생한다")
        void resolveArgument_notAuthenticated_required_throwsException() throws Exception {
            // given
            SecurityContextHolder.clearContext();

            Method method = TestController.class.getMethod("testMethod", String.class);
            MethodParameter parameter = new MethodParameter(method, 0);

            // when & then
            assertThatThrownBy(() -> resolver.resolveArgument(parameter, null, null, null))
                .isInstanceOf(CustomException.class);
        }

        @Test
        @DisplayName("인증되지 않은 경우 필수가 아니면 null을 반환한다")
        void resolveArgument_notAuthenticated_notRequired_returnsNull() throws Exception {
            // given
            SecurityContextHolder.clearContext();

            Method method = TestController.class.getMethod("testMethodOptional", String.class);
            MethodParameter parameter = new MethodParameter(method, 0);

            // when
            Object result = resolver.resolveArgument(parameter, null, null, null);

            // then
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("익명 사용자인 경우 필수가 아니면 null을 반환한다")
        void resolveArgument_anonymousUser_notRequired_returnsNull() throws Exception {
            // given
            SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("anonymousUser", null)
            );

            Method method = TestController.class.getMethod("testMethodOptional", String.class);
            MethodParameter parameter = new MethodParameter(method, 0);

            // when
            Object result = resolver.resolveArgument(parameter, null, null, null);

            // then
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("기타 principal 타입일 경우 toString()을 반환한다")
        void resolveArgument_withOtherPrincipal_returnsToString() throws Exception {
            // given
            Object customPrincipal = new Object() {
                @Override
                public String toString() {
                    return "custom-user-id";
                }
            };
            UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(customPrincipal, null, java.util.Collections.emptyList());
            SecurityContextHolder.getContext().setAuthentication(auth);

            Method method = TestController.class.getMethod("testMethod", String.class);
            MethodParameter parameter = new MethodParameter(method, 0);

            // when
            Object result = resolver.resolveArgument(parameter, null, null, null);

            // then
            assertThat(result).isEqualTo("custom-user-id");
        }
    }

    // Test controller for method parameter extraction
    static class TestController {
        public void testMethod(@CurrentUser String userId) {}
        public void testMethodOptional(@CurrentUser(required = false) String userId) {}
        public void testMethodWithoutAnnotation(String userId) {}
        public void testMethodWithLong(@CurrentUser Long userId) {}
    }
}
