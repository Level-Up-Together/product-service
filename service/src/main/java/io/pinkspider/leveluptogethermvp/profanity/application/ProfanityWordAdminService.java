package io.pinkspider.leveluptogethermvp.profanity.application;

import io.pinkspider.global.exception.CustomException;
import io.pinkspider.leveluptogethermvp.profanity.domain.dto.ProfanityWordPageResponse;
import io.pinkspider.leveluptogethermvp.profanity.domain.dto.ProfanityWordRequest;
import io.pinkspider.leveluptogethermvp.profanity.domain.dto.ProfanityWordResponse;
import io.pinkspider.leveluptogethermvp.profanity.domain.entity.ProfanityWord;
import io.pinkspider.leveluptogethermvp.profanity.infrastructure.ProfanityWordRepository;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * ProfanityWord Admin CRUD 서비스
 * Admin Internal API를 통해 호출되며, 캐시를 자동 무효화
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true, transactionManager = "metaTransactionManager")
public class ProfanityWordAdminService {

    private final ProfanityWordRepository profanityWordRepository;

    public List<ProfanityWordResponse> getAllProfanityWords() {
        return profanityWordRepository.findAll().stream()
            .map(ProfanityWordResponse::from)
            .collect(Collectors.toList());
    }

    public List<ProfanityWordResponse> getActiveProfanityWords() {
        return profanityWordRepository.findAllByIsActiveTrue().stream()
            .map(ProfanityWordResponse::from)
            .collect(Collectors.toList());
    }

    public ProfanityWordPageResponse searchProfanityWords(String keyword, Pageable pageable) {
        return ProfanityWordPageResponse.from(
            profanityWordRepository.searchByKeyword(keyword, pageable)
                .map(ProfanityWordResponse::from));
    }

    public ProfanityWordResponse getProfanityWord(Long id) {
        ProfanityWord word = profanityWordRepository.findById(id)
            .orElseThrow(() -> new CustomException("404", "error.profanity.not_found"));
        return ProfanityWordResponse.from(word);
    }

    @CacheEvict(value = "profanityWords", allEntries = true)
    @Transactional(transactionManager = "metaTransactionManager")
    public ProfanityWordResponse createProfanityWord(ProfanityWordRequest request) {
        String locale = request.getLocale() != null ? request.getLocale() : "ko";
        if (profanityWordRepository.existsByLocaleAndWord(locale, request.getWord())) {
            throw new CustomException("400", "error.profanity.duplicate");
        }

        ProfanityWord word = ProfanityWord.builder()
            .locale(locale)
            .word(request.getWord())
            .category(request.getCategory())
            .severity(request.getSeverity())
            .isActive(request.getIsActive() != null ? request.getIsActive() : true)
            .description(request.getDescription())
            .build();

        ProfanityWord saved = profanityWordRepository.save(word);
        log.info("금칙어 생성: word={}", saved.getWord());
        return ProfanityWordResponse.from(saved);
    }

    @CacheEvict(value = "profanityWords", allEntries = true)
    @Transactional(transactionManager = "metaTransactionManager")
    public ProfanityWordResponse updateProfanityWord(Long id, ProfanityWordRequest request) {
        ProfanityWord word = profanityWordRepository.findById(id)
            .orElseThrow(() -> new CustomException("404", "error.profanity.not_found"));

        String locale = request.getLocale() != null ? request.getLocale() : word.getLocale();
        if ((!word.getWord().equals(request.getWord()) || !word.getLocale().equals(locale))
            && profanityWordRepository.existsByLocaleAndWord(locale, request.getWord())) {
            throw new CustomException("400", "error.profanity.duplicate");
        }

        word.setLocale(locale);
        word.setWord(request.getWord());
        word.setCategory(request.getCategory());
        word.setSeverity(request.getSeverity());
        word.setIsActive(request.getIsActive() != null ? request.getIsActive() : true);
        word.setDescription(request.getDescription());

        ProfanityWord saved = profanityWordRepository.save(word);
        log.info("금칙어 수정: id={}, word={}", id, saved.getWord());
        return ProfanityWordResponse.from(saved);
    }

    @CacheEvict(value = "profanityWords", allEntries = true)
    @Transactional(transactionManager = "metaTransactionManager")
    public void deleteProfanityWord(Long id) {
        if (!profanityWordRepository.existsById(id)) {
            throw new CustomException("404", "error.profanity.not_found");
        }
        profanityWordRepository.deleteById(id);
        log.info("금칙어 삭제: id={}", id);
    }

    @CacheEvict(value = "profanityWords", allEntries = true)
    @Transactional(transactionManager = "metaTransactionManager")
    public ProfanityWordResponse toggleActive(Long id) {
        ProfanityWord word = profanityWordRepository.findById(id)
            .orElseThrow(() -> new CustomException("404", "error.profanity.not_found"));

        word.setIsActive(!word.getIsActive());
        ProfanityWord saved = profanityWordRepository.save(word);
        log.info("금칙어 활성 상태 변경: id={}, isActive={}", id, saved.getIsActive());
        return ProfanityWordResponse.from(saved);
    }
}
