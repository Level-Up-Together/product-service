package io.pinkspider.leveluptogethermvp.gamificationservice.domain.entity;

import io.pinkspider.global.domain.auditentity.LocalDateTimeBaseEntity;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.enums.TitlePosition;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.Comment;

@Entity
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table(name = "user_title",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_user_title",
        columnNames = {"user_id", "title_id"}
    )
)
@Comment("유저 칭호")
public class UserTitle extends LocalDateTimeBaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    @Comment("ID")
    private Long id;

    @NotNull
    @Column(name = "user_id", nullable = false)
    @Comment("유저 ID")
    private String userId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "title_id", nullable = false)
    @Comment("칭호")
    private Title title;

    @Column(name = "acquired_at")
    @Comment("획득 일시")
    private LocalDateTime acquiredAt;

    @NotNull
    @Column(name = "is_equipped", nullable = false)
    @Comment("장착 여부")
    @Builder.Default
    private Boolean isEquipped = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "equipped_position", length = 10)
    @Comment("장착 위치 (LEFT: 좌측, RIGHT: 우측)")
    private TitlePosition equippedPosition;

    public void equip(TitlePosition position) {
        this.isEquipped = true;
        this.equippedPosition = position;
    }

    public void unequip() {
        this.isEquipped = false;
        this.equippedPosition = null;
    }

    /**
     * @deprecated Use {@link #equip(TitlePosition)} instead
     */
    @Deprecated
    public void equip() {
        this.isEquipped = true;
    }
}
