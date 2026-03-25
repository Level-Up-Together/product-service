package io.pinkspider.leveluptogethermvp.profanity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.pinkspider.global.exception.CustomException;
import io.pinkspider.leveluptogethermvp.profanity.domain.dto.ProfanityWordPageResponse;
import io.pinkspider.leveluptogethermvp.profanity.domain.dto.ProfanityWordRequest;
import io.pinkspider.leveluptogethermvp.profanity.domain.dto.ProfanityWordResponse;
import io.pinkspider.leveluptogethermvp.profanity.domain.entity.ProfanityWord;
import io.pinkspider.leveluptogethermvp.profanity.domain.enums.ProfanityCategory;
import io.pinkspider.leveluptogethermvp.profanity.domain.enums.ProfanitySeverity;
import io.pinkspider.leveluptogethermvp.profanity.infrastructure.ProfanityWordRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProfanityWordAdminService 단위 테스트")
class ProfanityWordAdminServiceTest {

    @Mock
    private ProfanityWordRepository profanityWordRepository;

    @InjectMocks
    private ProfanityWordAdminService profanityWordAdminService;

    private ProfanityWord sampleWord;
    private ProfanityWordRequest sampleRequest;

    @BeforeEach
    void setUp() {
        sampleWord = ProfanityWord.builder()
            .id(1L)
            .word("욕설단어")
            .category(ProfanityCategory.GENERAL)
            .severity(ProfanitySeverity.MEDIUM)
            .isActive(true)
            .description("테스트용 금칙어")
            .build();

        sampleRequest = ProfanityWordRequest.builder()
            .word("욕설단어")
            .category(ProfanityCategory.GENERAL)
            .severity(ProfanitySeverity.MEDIUM)
            .isActive(true)
            .description("테스트용 금칙어")
            .build();
    }

    private ProfanityWord buildWord(Long id, String word, ProfanityCategory category,
        ProfanitySeverity severity, boolean isActive) {
        return ProfanityWord.builder()
            .id(id)
            .word(word)
            .category(category)
            .severity(severity)
            .isActive(isActive)
            .description("설명")
            .build();
    }

    @Nested
    @DisplayName("getAllProfanityWords 테스트")
    class GetAllProfanityWordsTest {

        @Test
        @DisplayName("전체 금칙어 목록을 반환한다")
        void getAllProfanityWords_success() {
            // given
            List<ProfanityWord> words = List.of(
                buildWord(1L, "단어1", ProfanityCategory.GENERAL, ProfanitySeverity.LOW, true),
                buildWord(2L, "단어2", ProfanityCategory.SEXUAL, ProfanitySeverity.HIGH, false)
            );
            when(profanityWordRepository.findAll()).thenReturn(words);

            // when
            List<ProfanityWordResponse> result = profanityWordAdminService.getAllProfanityWords();

            // then
            assertThat(result).hasSize(2);
            assertThat(result.get(0).getWord()).isEqualTo("단어1");
            assertThat(result.get(1).getWord()).isEqualTo("단어2");
            verify(profanityWordRepository).findAll();
        }

        @Test
        @DisplayName("금칙어가 없으면 빈 목록을 반환한다")
        void getAllProfanityWords_emptyList() {
            // given
            when(profanityWordRepository.findAll()).thenReturn(List.of());

            // when
            List<ProfanityWordResponse> result = profanityWordAdminService.getAllProfanityWords();

            // then
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("getActiveProfanityWords 테스트")
    class GetActiveProfanityWordsTest {

        @Test
        @DisplayName("활성화된 금칙어 목록만 반환한다")
        void getActiveProfanityWords_success() {
            // given
            List<ProfanityWord> activeWords = List.of(
                buildWord(1L, "활성단어", ProfanityCategory.GENERAL, ProfanitySeverity.LOW, true)
            );
            when(profanityWordRepository.findAllByIsActiveTrue()).thenReturn(activeWords);

            // when
            List<ProfanityWordResponse> result = profanityWordAdminService.getActiveProfanityWords();

            // then
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getIsActive()).isTrue();
            verify(profanityWordRepository).findAllByIsActiveTrue();
        }

        @Test
        @DisplayName("활성 금칙어가 없으면 빈 목록을 반환한다")
        void getActiveProfanityWords_emptyList() {
            // given
            when(profanityWordRepository.findAllByIsActiveTrue()).thenReturn(List.of());

            // when
            List<ProfanityWordResponse> result = profanityWordAdminService.getActiveProfanityWords();

            // then
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("searchProfanityWords 테스트")
    class SearchProfanityWordsTest {

        @Test
        @DisplayName("키워드로 금칙어를 검색하여 페이지 결과를 반환한다")
        void searchProfanityWords_success() {
            // given
            Pageable pageable = PageRequest.of(0, 10);
            List<ProfanityWord> words = List.of(sampleWord);
            Page<ProfanityWord> page = new PageImpl<>(words, pageable, 1);
            when(profanityWordRepository.searchByKeyword("욕설", pageable)).thenReturn(page);

            // when
            ProfanityWordPageResponse result = profanityWordAdminService.searchProfanityWords("욕설", pageable);

            // then
            assertThat(result).isNotNull();
            assertThat(result.totalElements()).isEqualTo(1);
            assertThat(result.content()).hasSize(1);
            assertThat(result.content().get(0).getWord()).isEqualTo("욕설단어");
        }

        @Test
        @DisplayName("검색 결과가 없으면 빈 페이지를 반환한다")
        void searchProfanityWords_noResult() {
            // given
            Pageable pageable = PageRequest.of(0, 10);
            Page<ProfanityWord> emptyPage = new PageImpl<>(List.of(), pageable, 0);
            when(profanityWordRepository.searchByKeyword("없는키워드", pageable)).thenReturn(emptyPage);

            // when
            ProfanityWordPageResponse result = profanityWordAdminService.searchProfanityWords("없는키워드", pageable);

            // then
            assertThat(result.totalElements()).isEqualTo(0);
            assertThat(result.content()).isEmpty();
        }

        @Test
        @DisplayName("null 키워드로도 검색할 수 있다")
        void searchProfanityWords_nullKeyword() {
            // given
            Pageable pageable = PageRequest.of(0, 10);
            Page<ProfanityWord> page = new PageImpl<>(List.of(sampleWord), pageable, 1);
            when(profanityWordRepository.searchByKeyword(null, pageable)).thenReturn(page);

            // when
            ProfanityWordPageResponse result = profanityWordAdminService.searchProfanityWords(null, pageable);

            // then
            assertThat(result.totalElements()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("getProfanityWord 테스트")
    class GetProfanityWordTest {

        @Test
        @DisplayName("ID로 금칙어를 조회한다")
        void getProfanityWord_success() {
            // given
            when(profanityWordRepository.findById(1L)).thenReturn(Optional.of(sampleWord));

            // when
            ProfanityWordResponse result = profanityWordAdminService.getProfanityWord(1L);

            // then
            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(1L);
            assertThat(result.getWord()).isEqualTo("욕설단어");
            assertThat(result.getCategory()).isEqualTo(ProfanityCategory.GENERAL);
            assertThat(result.getSeverity()).isEqualTo(ProfanitySeverity.MEDIUM);
        }

        @Test
        @DisplayName("존재하지 않는 ID면 CustomException을 던진다")
        void getProfanityWord_notFound() {
            // given
            when(profanityWordRepository.findById(999L)).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> profanityWordAdminService.getProfanityWord(999L))
                .isInstanceOf(CustomException.class);
        }
    }

    @Nested
    @DisplayName("createProfanityWord 테스트")
    class CreateProfanityWordTest {

        @Test
        @DisplayName("새 금칙어를 생성하고 반환한다")
        void createProfanityWord_success() {
            // given
            when(profanityWordRepository.existsByLocaleAndWord("ko","욕설단어")).thenReturn(false);
            when(profanityWordRepository.save(any(ProfanityWord.class))).thenReturn(sampleWord);

            // when
            ProfanityWordResponse result = profanityWordAdminService.createProfanityWord(sampleRequest);

            // then
            assertThat(result).isNotNull();
            assertThat(result.getWord()).isEqualTo("욕설단어");
            assertThat(result.getCategory()).isEqualTo(ProfanityCategory.GENERAL);
            verify(profanityWordRepository).save(any(ProfanityWord.class));
        }

        @Test
        @DisplayName("이미 등록된 금칙어면 CustomException을 던진다")
        void createProfanityWord_duplicateWord() {
            // given
            when(profanityWordRepository.existsByLocaleAndWord("ko","욕설단어")).thenReturn(true);

            // when & then
            assertThatThrownBy(() -> profanityWordAdminService.createProfanityWord(sampleRequest))
                .isInstanceOf(CustomException.class);
            verify(profanityWordRepository, never()).save(any());
        }

        @Test
        @DisplayName("isActive가 null이면 기본값 true로 생성된다")
        void createProfanityWord_nullIsActiveDefaultsToTrue() {
            // given
            ProfanityWordRequest requestWithNullActive = ProfanityWordRequest.builder()
                .word("새단어")
                .category(ProfanityCategory.VIOLENCE)
                .severity(ProfanitySeverity.HIGH)
                .isActive(null)
                .build();

            ProfanityWord savedWord = buildWord(2L, "새단어", ProfanityCategory.VIOLENCE,
                ProfanitySeverity.HIGH, true);
            when(profanityWordRepository.existsByLocaleAndWord("ko","새단어")).thenReturn(false);
            when(profanityWordRepository.save(any(ProfanityWord.class))).thenReturn(savedWord);

            // when
            ProfanityWordResponse result = profanityWordAdminService.createProfanityWord(requestWithNullActive);

            // then
            assertThat(result.getIsActive()).isTrue();
        }
    }

    @Nested
    @DisplayName("updateProfanityWord 테스트")
    class UpdateProfanityWordTest {

        @Test
        @DisplayName("기존 금칙어를 수정하고 반환한다")
        void updateProfanityWord_success() {
            // given
            ProfanityWordRequest updateRequest = ProfanityWordRequest.builder()
                .word("수정된단어")
                .category(ProfanityCategory.SEXUAL)
                .severity(ProfanitySeverity.HIGH)
                .isActive(true)
                .description("수정된 설명")
                .build();

            ProfanityWord updatedWord = buildWord(1L, "수정된단어", ProfanityCategory.SEXUAL,
                ProfanitySeverity.HIGH, true);

            when(profanityWordRepository.findById(1L)).thenReturn(Optional.of(sampleWord));
            when(profanityWordRepository.existsByLocaleAndWord("ko","수정된단어")).thenReturn(false);
            when(profanityWordRepository.save(any(ProfanityWord.class))).thenReturn(updatedWord);

            // when
            ProfanityWordResponse result = profanityWordAdminService.updateProfanityWord(1L, updateRequest);

            // then
            assertThat(result).isNotNull();
            assertThat(result.getWord()).isEqualTo("수정된단어");
            verify(profanityWordRepository).save(any(ProfanityWord.class));
        }

        @Test
        @DisplayName("같은 단어로 수정하면 중복 체크를 수행하지 않는다")
        void updateProfanityWord_sameWord_noDuplicateCheck() {
            // given
            ProfanityWordRequest sameWordRequest = ProfanityWordRequest.builder()
                .word("욕설단어")
                .category(ProfanityCategory.GENERAL)
                .severity(ProfanitySeverity.HIGH)
                .isActive(false)
                .build();

            ProfanityWord updatedWord = buildWord(1L, "욕설단어", ProfanityCategory.GENERAL,
                ProfanitySeverity.HIGH, false);

            when(profanityWordRepository.findById(1L)).thenReturn(Optional.of(sampleWord));
            when(profanityWordRepository.save(any(ProfanityWord.class))).thenReturn(updatedWord);

            // when
            ProfanityWordResponse result = profanityWordAdminService.updateProfanityWord(1L, sameWordRequest);

            // then
            assertThat(result).isNotNull();
            verify(profanityWordRepository, never()).existsByLocaleAndWord(anyString(), anyString());
        }

        @Test
        @DisplayName("존재하지 않는 ID면 CustomException을 던진다")
        void updateProfanityWord_notFound() {
            // given
            when(profanityWordRepository.findById(999L)).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> profanityWordAdminService.updateProfanityWord(999L, sampleRequest))
                .isInstanceOf(CustomException.class);
        }

        @Test
        @DisplayName("다른 단어로 변경 시 이미 등록된 단어면 CustomException을 던진다")
        void updateProfanityWord_duplicateNewWord() {
            // given
            ProfanityWordRequest updateRequest = ProfanityWordRequest.builder()
                .word("이미존재하는단어")
                .category(ProfanityCategory.GENERAL)
                .severity(ProfanitySeverity.LOW)
                .isActive(true)
                .build();

            when(profanityWordRepository.findById(1L)).thenReturn(Optional.of(sampleWord));
            when(profanityWordRepository.existsByLocaleAndWord("ko","이미존재하는단어")).thenReturn(true);

            // when & then
            assertThatThrownBy(() -> profanityWordAdminService.updateProfanityWord(1L, updateRequest))
                .isInstanceOf(CustomException.class);
            verify(profanityWordRepository, never()).save(any());
        }

        @Test
        @DisplayName("isActive가 null이면 기본값 true로 수정된다")
        void updateProfanityWord_nullIsActiveDefaultsToTrue() {
            // given
            ProfanityWordRequest requestWithNullActive = ProfanityWordRequest.builder()
                .word("욕설단어")
                .category(ProfanityCategory.GENERAL)
                .severity(ProfanitySeverity.MEDIUM)
                .isActive(null)
                .build();

            ProfanityWord updatedWord = buildWord(1L, "욕설단어", ProfanityCategory.GENERAL,
                ProfanitySeverity.MEDIUM, true);

            when(profanityWordRepository.findById(1L)).thenReturn(Optional.of(sampleWord));
            when(profanityWordRepository.save(any(ProfanityWord.class))).thenReturn(updatedWord);

            // when
            ProfanityWordResponse result = profanityWordAdminService.updateProfanityWord(1L, requestWithNullActive);

            // then
            assertThat(result.getIsActive()).isTrue();
        }
    }

    @Nested
    @DisplayName("deleteProfanityWord 테스트")
    class DeleteProfanityWordTest {

        @Test
        @DisplayName("금칙어를 삭제한다")
        void deleteProfanityWord_success() {
            // given
            when(profanityWordRepository.existsById(1L)).thenReturn(true);

            // when
            profanityWordAdminService.deleteProfanityWord(1L);

            // then
            verify(profanityWordRepository).deleteById(1L);
        }

        @Test
        @DisplayName("존재하지 않는 ID면 CustomException을 던진다")
        void deleteProfanityWord_notFound() {
            // given
            when(profanityWordRepository.existsById(999L)).thenReturn(false);

            // when & then
            assertThatThrownBy(() -> profanityWordAdminService.deleteProfanityWord(999L))
                .isInstanceOf(CustomException.class);
            verify(profanityWordRepository, never()).deleteById(any());
        }
    }

    @Nested
    @DisplayName("toggleActive 테스트")
    class ToggleActiveTest {

        @Test
        @DisplayName("활성화된 금칙어를 비활성화로 전환한다")
        void toggleActive_activeToInactive() {
            // given
            ProfanityWord inactiveWord = buildWord(1L, "욕설단어", ProfanityCategory.GENERAL,
                ProfanitySeverity.MEDIUM, false);

            when(profanityWordRepository.findById(1L)).thenReturn(Optional.of(sampleWord));
            when(profanityWordRepository.save(any(ProfanityWord.class))).thenReturn(inactiveWord);

            // when
            ProfanityWordResponse result = profanityWordAdminService.toggleActive(1L);

            // then
            assertThat(result.getIsActive()).isFalse();
            verify(profanityWordRepository).save(any(ProfanityWord.class));
        }

        @Test
        @DisplayName("비활성화된 금칙어를 활성화로 전환한다")
        void toggleActive_inactiveToActive() {
            // given
            ProfanityWord inactiveWord = buildWord(1L, "욕설단어", ProfanityCategory.GENERAL,
                ProfanitySeverity.MEDIUM, false);
            ProfanityWord activeWord = buildWord(1L, "욕설단어", ProfanityCategory.GENERAL,
                ProfanitySeverity.MEDIUM, true);

            when(profanityWordRepository.findById(1L)).thenReturn(Optional.of(inactiveWord));
            when(profanityWordRepository.save(any(ProfanityWord.class))).thenReturn(activeWord);

            // when
            ProfanityWordResponse result = profanityWordAdminService.toggleActive(1L);

            // then
            assertThat(result.getIsActive()).isTrue();
        }

        @Test
        @DisplayName("존재하지 않는 ID면 CustomException을 던진다")
        void toggleActive_notFound() {
            // given
            when(profanityWordRepository.findById(999L)).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> profanityWordAdminService.toggleActive(999L))
                .isInstanceOf(CustomException.class);
        }
    }
}
