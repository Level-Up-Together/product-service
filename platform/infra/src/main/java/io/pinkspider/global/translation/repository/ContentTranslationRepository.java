package io.pinkspider.global.translation.repository;

import io.pinkspider.global.translation.entity.ContentTranslation;
import io.pinkspider.global.translation.enums.ContentType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * 번역 캐시 Repository
 */
@Repository
public interface ContentTranslationRepository extends JpaRepository<ContentTranslation, Long> {

    /**
     * 특정 콘텐츠의 특정 필드 번역 조회
     */
    Optional<ContentTranslation> findByContentTypeAndContentIdAndFieldNameAndTargetLocale(
        ContentType contentType,
        Long contentId,
        String fieldName,
        String targetLocale
    );

    /**
     * 특정 콘텐츠의 모든 번역 조회
     */
    List<ContentTranslation> findByContentTypeAndContentIdAndTargetLocale(
        ContentType contentType,
        Long contentId,
        String targetLocale
    );

    /**
     * 특정 콘텐츠의 모든 번역 삭제 (콘텐츠 삭제 시)
     */
    @Modifying
    @Query("DELETE FROM ContentTranslation ct WHERE ct.contentType = :contentType AND ct.contentId = :contentId")
    void deleteByContentTypeAndContentId(
        @Param("contentType") ContentType contentType,
        @Param("contentId") Long contentId
    );

    /**
     * 원문 해시로 번역 조회 (캐시 유효성 검증용)
     */
    Optional<ContentTranslation> findByContentTypeAndContentIdAndFieldNameAndTargetLocaleAndOriginalHash(
        ContentType contentType,
        Long contentId,
        String fieldName,
        String targetLocale,
        String originalHash
    );
}
