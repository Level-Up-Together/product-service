package io.pinkspider.leveluptogethermvp.userservice.unit.user.domain.entity;

import io.pinkspider.global.domain.auditentity.DatetimeAndSubjectBaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.LinkedHashSet;
import java.util.Set;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Setter
@Entity
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table(name = "terms")
public class Term extends DatetimeAndSubjectBaseEntity {

    @Id
    @Column(name = "id", nullable = false)
    private Long id;

    @Size(max = 100)
    @NotNull
    @Column(name = "code", nullable = false, length = 100)
    private String code;

    @Size(max = 255)
    @NotNull
    @Column(name = "title", nullable = false)
    private String title;

//    @Lob
    @Column(name = "description")
    @JdbcTypeCode(SqlTypes.VARCHAR)
    private String description;

    @Size(max = 100)
    @Column(name = "type", length = 100)
    private String type;

    @NotNull
    @ColumnDefault("0")
    @Column(name = "is_required", nullable = false)
    private Boolean isRequired = false;

    @OneToMany(mappedBy = "terms")
    private Set<TermVersion> termVersions = new LinkedHashSet<>();

}
