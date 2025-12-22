package io.pinkspider.leveluptogethermvp.userservice.unit.user.domain.entity;

import io.pinkspider.global.converter.CryptoConverter;
import io.pinkspider.global.domain.auditentity.LocalDateTimeBaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import java.util.LinkedHashSet;
import java.util.Set;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.UuidGenerator;


@Entity
@Getter
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table(name = "users")
public class Users extends LocalDateTimeBaseEntity {

    @Id
    @UuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    private String id;

    @Column(name = "nickname", length = 50)
    private String nickname;

    @Convert(converter = CryptoConverter.class)
    @NotNull
    private String email;

    @Column
    private String picture;

    @NotNull
    private String provider;

    @lombok.Builder.Default
    @Column(name = "nickname_set", nullable = false)
    private boolean nicknameSet = false;

    @Setter
    @OneToMany(mappedBy = "users")
    private Set<UserTermAgreement> userTermAgreements = new LinkedHashSet<>();

    public Users update(String nickname, String picture) {
        this.nickname = nickname;
        this.picture = picture;

        return this;
    }

    public void updatePicture(String picture) {
        this.picture = picture;
    }

    public void updateNickname(String nickname) {
        this.nickname = nickname;
        this.nicknameSet = true;
    }

    public boolean isNicknameSet() {
        return nicknameSet;
    }

    /**
     * 표시용 이름 반환 (닉네임 > 이메일 앞부분)
     */
    public String getDisplayName() {
        if (nickname != null && !nickname.isBlank()) {
            return nickname;
        }
        // 이메일의 @ 앞부분 반환
        if (email != null && email.contains("@")) {
            return email.substring(0, email.indexOf("@"));
        }
        return "사용자";
    }
}
