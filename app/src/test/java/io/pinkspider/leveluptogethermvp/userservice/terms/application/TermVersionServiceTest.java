package io.pinkspider.leveluptogethermvp.userservice.terms.application;

import static io.pinkspider.global.test.TestReflectionUtils.setId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.pinkspider.global.exception.CustomException;
import io.pinkspider.leveluptogethermvp.userservice.terms.infrastructure.TermVersionRepository;
import io.pinkspider.leveluptogethermvp.userservice.unit.user.domain.entity.TermVersion;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TermVersionServiceTest {

    @Mock
    private TermVersionRepository termVersionRepository;

    @InjectMocks
    private TermVersionService termVersionService;

    private TermVersion createTestTermVersion(Long id, String version) {
        TermVersion termVersion = TermVersion.builder()
            .version(version)
            .content("약관 내용")
            .build();
        setId(termVersion, id);
        return termVersion;
    }

    @Nested
    @DisplayName("findById 테스트")
    class FindByIdTest {

        @Test
        @DisplayName("약관 버전을 조회한다")
        void findById_success() {
            // given
            Long termVersionId = 1L;
            TermVersion termVersion = createTestTermVersion(termVersionId, "1.0");

            when(termVersionRepository.findById(termVersionId)).thenReturn(Optional.of(termVersion));

            // when
            TermVersion result = termVersionService.findById(termVersionId);

            // then
            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(termVersionId);
            assertThat(result.getVersion()).isEqualTo("1.0");
        }

        @Test
        @DisplayName("약관 버전이 없으면 예외를 발생시킨다")
        void findById_notFound_throwsException() {
            // given
            Long termVersionId = 999L;

            when(termVersionRepository.findById(termVersionId)).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> termVersionService.findById(termVersionId))
                .isInstanceOf(CustomException.class);
        }
    }

    @Nested
    @DisplayName("save 테스트")
    class SaveTest {

        @Test
        @DisplayName("약관 버전을 저장한다")
        void save_success() {
            // given
            TermVersion termVersion = createTestTermVersion(1L, "2.0");

            // when
            termVersionService.save(termVersion);

            // then
            verify(termVersionRepository).save(termVersion);
        }
    }
}
