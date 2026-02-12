package io.pinkspider.global.translation.entity;

import io.pinkspider.global.translation.enums.ContentType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

/**
 * 번역 캐시 엔티티
 * 콘텐츠별 번역 결과를 DB에 영구 저장
 */
@Entity
@Table(
    name = "content_translation",
    indexes = {
        @Index(name = "idx_content_translation_lookup",
            columnList = "content_type, content_id, field_name, target_locale")
    }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContentTranslation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 콘텐츠 타입 (FEED, GUILD_POST, FEED_COMMENT, GUILD_COMMENT)
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "content_type", nullable = false, length = 50)
    private ContentType contentType;

    /**
     * 콘텐츠 ID
     */
    @Column(name = "content_id", nullable = false)
    private Long contentId;

    /**
     * 번역 필드명 (title, content, description 등)
     */
    @Column(name = "field_name", nullable = false, length = 50)
    private String fieldName;

    /**
     * 원본 언어 코드 (ISO 639-1)
     */
    @Column(name = "source_locale", nullable = false, length = 5)
    private String sourceLocale;

    /**
     * 대상 언어 코드 (ISO 639-1)
     */
    @Column(name = "target_locale", nullable = false, length = 5)
    private String targetLocale;

    /**
     * 원문 해시 (SHA-256)
     * 원문 변경 감지에 사용
     */
    @Column(name = "original_hash", nullable = false, length = 64)
    private String originalHash;

    /**
     * 번역된 텍스트
     */
    @Column(name = "translated_text", nullable = false, columnDefinition = "TEXT")
    private String translatedText;

    /**
     * 생성 일시
     */
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * 번역 결과 업데이트
     */
    public void updateTranslation(String newHash, String newTranslatedText) {
        this.originalHash = newHash;
        this.translatedText = newTranslatedText;
    }
}
