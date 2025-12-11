package io.pinkspider.leveluptogethermvp.guildservice.domain.entity;

import io.pinkspider.global.domain.auditentity.LocalDateTimeBaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
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
@Table(name = "guild_post_comment")
@Comment("길드 게시글 댓글")
public class GuildPostComment extends LocalDateTimeBaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    @Comment("댓글 ID")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id", nullable = false)
    @Comment("게시글")
    private GuildPost post;

    @NotNull
    @Column(name = "author_id", nullable = false)
    @Comment("작성자 ID")
    private String authorId;

    @Size(max = 50)
    @Column(name = "author_nickname", length = 50)
    @Comment("작성자 닉네임")
    private String authorNickname;

    @NotNull
    @Size(max = 1000)
    @Column(name = "content", nullable = false, length = 1000)
    @Comment("댓글 내용")
    private String content;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    @Comment("상위 댓글")
    private GuildPostComment parent;

    @Builder.Default
    @OneToMany(mappedBy = "parent")
    private List<GuildPostComment> replies = new ArrayList<>();

    @Column(name = "is_deleted")
    @Comment("삭제 여부")
    @Builder.Default
    private Boolean isDeleted = false;

    @Column(name = "deleted_at")
    @Comment("삭제 시간")
    private LocalDateTime deletedAt;

    public boolean isAuthor(String userId) {
        return this.authorId.equals(userId);
    }

    public boolean isReply() {
        return this.parent != null;
    }

    public void delete() {
        this.isDeleted = true;
        this.deletedAt = LocalDateTime.now();
    }

    public void update(String content) {
        this.content = content;
    }
}
