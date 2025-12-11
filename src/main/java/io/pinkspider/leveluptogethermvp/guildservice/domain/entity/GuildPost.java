package io.pinkspider.leveluptogethermvp.guildservice.domain.entity;

import io.pinkspider.global.domain.auditentity.LocalDateTimeBaseEntity;
import io.pinkspider.leveluptogethermvp.guildservice.domain.enums.GuildPostType;
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
@Table(name = "guild_post")
@Comment("길드 게시글")
public class GuildPost extends LocalDateTimeBaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    @Comment("게시글 ID")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "guild_id", nullable = false)
    @Comment("길드")
    private Guild guild;

    @NotNull
    @Column(name = "author_id", nullable = false)
    @Comment("작성자 ID")
    private String authorId;

    @Size(max = 50)
    @Column(name = "author_nickname", length = 50)
    @Comment("작성자 닉네임")
    private String authorNickname;

    @NotNull
    @Size(max = 200)
    @Column(name = "title", nullable = false, length = 200)
    @Comment("제목")
    private String title;

    @NotNull
    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    @Comment("내용")
    private String content;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "post_type", nullable = false, length = 20)
    @Comment("게시글 유형")
    @Builder.Default
    private GuildPostType postType = GuildPostType.NORMAL;

    @Column(name = "is_pinned")
    @Comment("상단 고정 여부")
    @Builder.Default
    private Boolean isPinned = false;

    @Column(name = "view_count")
    @Comment("조회수")
    @Builder.Default
    private Integer viewCount = 0;

    @Column(name = "comment_count")
    @Comment("댓글 수")
    @Builder.Default
    private Integer commentCount = 0;

    @Column(name = "is_deleted")
    @Comment("삭제 여부")
    @Builder.Default
    private Boolean isDeleted = false;

    @Column(name = "deleted_at")
    @Comment("삭제 시간")
    private LocalDateTime deletedAt;

    @Builder.Default
    @OneToMany(mappedBy = "post")
    private List<GuildPostComment> comments = new ArrayList<>();

    public boolean isNotice() {
        return this.postType == GuildPostType.NOTICE;
    }

    public boolean isAuthor(String userId) {
        return this.authorId.equals(userId);
    }

    public void incrementViewCount() {
        this.viewCount++;
    }

    public void incrementCommentCount() {
        this.commentCount++;
    }

    public void decrementCommentCount() {
        if (this.commentCount > 0) {
            this.commentCount--;
        }
    }

    public void pin() {
        this.isPinned = true;
    }

    public void unpin() {
        this.isPinned = false;
    }

    public void delete() {
        this.isDeleted = true;
        this.deletedAt = LocalDateTime.now();
    }

    public void update(String title, String content) {
        this.title = title;
        this.content = content;
    }
}
