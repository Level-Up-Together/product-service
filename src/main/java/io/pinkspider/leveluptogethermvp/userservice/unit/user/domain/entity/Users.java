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

    private String name;

    @Convert(converter = CryptoConverter.class)
    @NotNull
    private String email;

    @Column
    private String picture;

    @NotNull
    private String provider;

    @Setter
    @OneToMany(mappedBy = "users")
    private Set<UserTermAgreement> userTermAgreements = new LinkedHashSet<>();

    public Users update(String name, String picture) {
        this.name = name;
        this.picture = picture;

        return this;
    }
}
