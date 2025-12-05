package io.pinkspider.leveluptogethermvp.userservice.oauth.domain;

import java.io.Serial;
import java.util.Collection;
import java.util.Objects;
import lombok.Builder;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

public class UserDetailsImpl implements UserDetails {

    @Serial
    private static final long serialVersionUID = 1L;

    @Getter
    private final String memberId;

    private final String userId;

    private final String password;

//    @Getter
//    private final MemberActivityModeEnum activityMode;

    @Getter
    private final Integer passwordFailCount;

    private Collection<? extends GrantedAuthority> authorities;

    @Builder
    public UserDetailsImpl(String memberId, String userId, String password, Integer passwordFailCount) {
        this.memberId = memberId;
        this.userId = userId;
        this.password = password;
//        this.activityMode = activityMode;
        this.passwordFailCount = passwordFailCount;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return this.password;
    }

    @Override
    public String getUsername() {
        return this.userId;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        UserDetailsImpl checkMemberDetail = (UserDetailsImpl) o;
        return Objects.equals(this.memberId, checkMemberDetail.getMemberId());
    }
}
