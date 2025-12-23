package io.pinkspider.leveluptogethermvp.profanity.domain.entity;

import io.pinkspider.global.domain.auditentity.LocalDateTimeBaseEntity;
import io.pinkspider.leveluptogethermvp.profanity.domain.enums.ProfanityCategory;
import io.pinkspider.leveluptogethermvp.profanity.domain.enums.ProfanitySeverity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.Comment;
import org.hibernate.annotations.Immutable;

/**
 * 비속어/금칙어 엔티티 (읽기 전용)
 * MVP 백엔드에서는 조회만 가능하며, 관리는 Admin 백엔드에서만 수행
 */
@Entity
@Getter
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Immutable
@Table(name = "profanity_word",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_profanity_word_word",
        columnNames = {"word"}
    )
)
@Comment("금칙어 관리")
public class ProfanityWord extends LocalDateTimeBaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    @Comment("ID")
    private Long id;

    @NotNull
    @Column(name = "word", nullable = false, length = 100)
    @Comment("금칙어")
    private String word;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 20)
    @Comment("카테고리")
    private ProfanityCategory category;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false, length = 20)
    @Comment("심각도")
    private ProfanitySeverity severity;

    @NotNull
    @Column(name = "is_active", nullable = false)
    @Comment("활성화 여부")
    private Boolean isActive;

    @Column(name = "description", length = 500)
    @Comment("설명")
    private String description;
}
