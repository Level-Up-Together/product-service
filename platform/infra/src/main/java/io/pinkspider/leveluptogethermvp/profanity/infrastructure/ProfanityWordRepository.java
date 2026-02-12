package io.pinkspider.leveluptogethermvp.profanity.infrastructure;

import io.pinkspider.leveluptogethermvp.profanity.domain.entity.ProfanityWord;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * 비속어/금칙어 리포지토리 (읽기 전용)
 */
@Repository
public interface ProfanityWordRepository extends JpaRepository<ProfanityWord, Long> {

    /**
     * 활성화된 모든 금칙어 조회
     */
    List<ProfanityWord> findAllByIsActiveTrue();
}
