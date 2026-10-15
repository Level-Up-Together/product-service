package io.pinkspider.leveluptogethermvp.userservice.oauth.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class UserDetailsImplTest {

    private UserDetailsImpl buildUserDetails(
            String memberId, String userId, String password, Integer passwordFailCount) {
        return UserDetailsImpl.builder()
                .memberId(memberId)
                .userId(userId)
                .password(password)
                .passwordFailCount(passwordFailCount)
                .build();
    }

    @Nested
    @DisplayName("생성자 및 getter 테스트")
    class ConstructorAndGetterTest {

        @Test
        @DisplayName("빌더로 생성한 UserDetailsImpl 필드를 올바르게 반환한다")
        void builder_createsWithCorrectFields() {
            // given
            UserDetailsImpl userDetails = buildUserDetails("member-1", "user-1", "password123", 0);

            // when & then
            assertThat(userDetails.getMemberId()).isEqualTo("member-1");
            assertThat(userDetails.getUsername()).isEqualTo("user-1");
            assertThat(userDetails.getPassword()).isEqualTo("password123");
            assertThat(userDetails.getPasswordFailCount()).isEqualTo(0);
        }

        @Test
        @DisplayName("getAuthorities는 null을 반환한다 (설정 전)")
        void getAuthorities_returnsNull_whenNotSet() {
            // given
            UserDetailsImpl userDetails = buildUserDetails("member-1", "user-1", "pw", 0);

            // when & then
            assertThat(userDetails.getAuthorities()).isNull();
        }
    }

    @Nested
    @DisplayName("UserDetails 인터페이스 메서드 테스트")
    class UserDetailsInterfaceTest {

        @Test
        @DisplayName("isAccountNonExpired는 항상 true를 반환한다")
        void isAccountNonExpired_returnsTrue() {
            // given
            UserDetailsImpl userDetails = buildUserDetails("member-1", "user-1", "pw", 0);

            // when & then
            assertThat(userDetails.isAccountNonExpired()).isTrue();
        }

        @Test
        @DisplayName("isAccountNonLocked는 항상 true를 반환한다")
        void isAccountNonLocked_returnsTrue() {
            // given
            UserDetailsImpl userDetails = buildUserDetails("member-1", "user-1", "pw", 0);

            // when & then
            assertThat(userDetails.isAccountNonLocked()).isTrue();
        }

        @Test
        @DisplayName("isCredentialsNonExpired는 항상 true를 반환한다")
        void isCredentialsNonExpired_returnsTrue() {
            // given
            UserDetailsImpl userDetails = buildUserDetails("member-1", "user-1", "pw", 0);

            // when & then
            assertThat(userDetails.isCredentialsNonExpired()).isTrue();
        }

        @Test
        @DisplayName("isEnabled는 항상 true를 반환한다")
        void isEnabled_returnsTrue() {
            // given
            UserDetailsImpl userDetails = buildUserDetails("member-1", "user-1", "pw", 0);

            // when & then
            assertThat(userDetails.isEnabled()).isTrue();
        }
    }

    @Nested
    @DisplayName("equals 테스트")
    class EqualsTest {

        @Test
        @DisplayName("동일한 객체는 equals가 true이다")
        void equals_sameObject_returnsTrue() {
            // given
            UserDetailsImpl userDetails = buildUserDetails("member-1", "user-1", "pw", 0);

            // when & then
            assertThat(userDetails.equals(userDetails)).isTrue();
        }

        @Test
        @DisplayName("같은 memberId를 가진 두 객체는 equals가 true이다")
        void equals_sameMemberId_returnsTrue() {
            // given
            UserDetailsImpl userDetails1 = buildUserDetails("member-1", "user-1", "pw1", 0);
            UserDetailsImpl userDetails2 = buildUserDetails("member-1", "user-2", "pw2", 3);

            // when & then
            assertThat(userDetails1.equals(userDetails2)).isTrue();
        }

        @Test
        @DisplayName("다른 memberId를 가진 두 객체는 equals가 false이다")
        void equals_differentMemberId_returnsFalse() {
            // given
            UserDetailsImpl userDetails1 = buildUserDetails("member-1", "user-1", "pw", 0);
            UserDetailsImpl userDetails2 = buildUserDetails("member-2", "user-1", "pw", 0);

            // when & then
            assertThat(userDetails1.equals(userDetails2)).isFalse();
        }

        @Test
        @DisplayName("null과 비교하면 equals가 false이다")
        void equals_null_returnsFalse() {
            // given
            UserDetailsImpl userDetails = buildUserDetails("member-1", "user-1", "pw", 0);

            // when & then
            assertThat(userDetails.equals(null)).isFalse();
        }

        @Test
        @DisplayName("다른 타입의 객체와 비교하면 equals가 false이다")
        void equals_differentType_returnsFalse() {
            // given
            UserDetailsImpl userDetails = buildUserDetails("member-1", "user-1", "pw", 0);

            // when & then
            assertThat(userDetails.equals("string")).isFalse();
        }

        @Test
        @DisplayName("memberId가 null인 두 객체는 equals가 true이다")
        void equals_bothNullMemberId_returnsTrue() {
            // given
            UserDetailsImpl userDetails1 = buildUserDetails(null, "user-1", "pw", 0);
            UserDetailsImpl userDetails2 = buildUserDetails(null, "user-2", "pw2", 1);

            // when & then
            assertThat(userDetails1.equals(userDetails2)).isTrue();
        }
    }
}
