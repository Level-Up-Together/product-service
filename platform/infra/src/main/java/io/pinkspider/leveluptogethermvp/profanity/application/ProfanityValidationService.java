package io.pinkspider.leveluptogethermvp.profanity.application;

import io.pinkspider.global.exception.CustomException;
import io.pinkspider.leveluptogethermvp.profanity.domain.entity.ProfanityWord;
import io.pinkspider.leveluptogethermvp.profanity.infrastructure.ProfanityWordRepository;
import jakarta.annotation.PostConstruct;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 비속어/금칙어 검증 서비스
 * 활성화된 금칙어 목록을 캐싱하여 성능 최적화
 */
@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProfanityValidationService {

    private final ProfanityWordRepository profanityWordRepository;

    @PostConstruct
    public void init() {
        log.info("ProfanityValidationService 초기화 - 금칙어 목록 캐싱 시작");
        try {
            Set<String> words = getActiveProfanityWords();
            log.info("금칙어 목록 캐싱 완료 - 총 {}개", words.size());
        } catch (Exception e) {
            log.warn("금칙어 목록 캐싱 실패: {}", e.getMessage());
        }
    }

    /**
     * 활성화된 금칙어 목록 조회 (캐싱)
     * 캐시명: profanityWords
     */
    @Cacheable(value = "profanityWords", unless = "#result == null || #result.isEmpty()")
    public Set<String> getActiveProfanityWords() {
        List<ProfanityWord> words = profanityWordRepository.findAllByIsActiveTrue();
        return words.stream()
            .map(ProfanityWord::getWord)
            .collect(Collectors.toSet());
    }

    /**
     * 단일 필드의 컨텐츠 금칙어 검증
     *
     * @param content 검증할 컨텐츠
     * @param fieldName 필드명 (에러 메시지에 사용)
     * @throws CustomException 금칙어가 포함된 경우
     */
    public void validateContent(String content, String fieldName) {
        if (content == null || content.trim().isEmpty()) {
            return;
        }

        Set<String> profanityWords = getActiveProfanityWords();

        for (String word : profanityWords) {
            if (content.contains(word)) {
                log.warn("금칙어 감지 - 필드: {}, 감지된 단어: {}", fieldName, word);
                throw new CustomException("PROFANITY_001",
                    fieldName + "에 부적절한 표현이 포함되어 있습니다.");
            }
        }
    }

    /**
     * 여러 필드의 컨텐츠를 한 번에 검증
     *
     * @param contents 필드명과 컨텐츠의 Map (key: 필드명, value: 컨텐츠)
     * @throws CustomException 금칙어가 포함된 경우
     */
    public void validateContents(Map<String, String> contents) {
        if (contents == null || contents.isEmpty()) {
            return;
        }

        for (Map.Entry<String, String> entry : contents.entrySet()) {
            validateContent(entry.getValue(), entry.getKey());
        }
    }

    /**
     * 금칙어 포함 여부만 확인 (예외를 던지지 않음)
     *
     * @param content 검증할 컨텐츠
     * @return 금칙어 포함 여부
     */
    public boolean containsProfanity(String content) {
        if (content == null || content.trim().isEmpty()) {
            return false;
        }

        Set<String> profanityWords = getActiveProfanityWords();

        for (String word : profanityWords) {
            if (content.contains(word)) {
                return true;
            }
        }

        return false;
    }

    /**
     * 컨텐츠에서 발견된 금칙어 목록 반환
     *
     * @param content 검증할 컨텐츠
     * @return 발견된 금칙어 목록
     */
    public List<String> findProfanityWords(String content) {
        if (content == null || content.trim().isEmpty()) {
            return List.of();
        }

        Set<String> profanityWords = getActiveProfanityWords();

        return profanityWords.stream()
            .filter(content::contains)
            .collect(Collectors.toList());
    }
}
