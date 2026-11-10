package io.pinkspider.leveluptogethermvp.towerservice.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.pinkspider.leveluptogethermvp.towerservice.TowerTestFixtures;
import io.pinkspider.leveluptogethermvp.towerservice.application.exception.TowerNpcException;
import io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerAdminPage;
import io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerNpcEntryAdminResponse;
import io.pinkspider.leveluptogethermvp.towerservice.domain.entity.TowerNpcEntry;
import io.pinkspider.leveluptogethermvp.towerservice.domain.enums.TowerNpcEntryStatus;
import io.pinkspider.leveluptogethermvp.towerservice.infrastructure.TowerNpcEntryRepository;
import io.pinkspider.leveluptogethermvp.towerservice.infrastructure.TowerNpcFormRepository;
import io.pinkspider.leveluptogethermvp.towerservice.infrastructure.TowerNpcRepository;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

/** LUT-760: 어드민 수집함 */
@ExtendWith(MockitoExtension.class)
class TowerNpcEntryAdminServiceTest {

    @Mock private TowerNpcRepository npcRepository;
    @Mock private TowerNpcFormRepository formRepository;
    @Mock private TowerNpcEntryRepository entryRepository;

    @InjectMocks private TowerNpcEntryAdminService service;

    private static TowerNpcEntry entry() {
        return TowerNpcEntry.builder()
                .id(5L)
                .npcId(3L)
                .userId("u1")
                .authorName("닉")
                .body("안녕")
                .email("a@b.c")
                .viewCount(3)
                .build();
    }

    @Test
    @SuppressWarnings("unchecked")
    @DisplayName("목록: 최신 먼저, 페이지 0 이상·크기 1~200 으로 고정, 이메일 포함 · NPC 없으면 150401")
    void list() {
        when(npcRepository.existsById(3L)).thenReturn(true);
        when(entryRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(entry()), PageRequest.of(0, 200), 1));

        TowerAdminPage<TowerNpcEntryAdminResponse> page =
                service.list(
                        3L,
                        TowerNpcEntryStatus.VISIBLE,
                        true,
                        LocalDate.of(2026, 11, 1),
                        LocalDate.of(2026, 11, 8),
                        -1,
                        999);

        assertThat(page.content()).hasSize(1);
        assertThat(page.content().get(0).email()).isEqualTo("a@b.c");
        assertThat(page.content().get(0).authorName()).isEqualTo("닉");
        assertThat(page.totalElements()).isEqualTo(1);
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(entryRepository).findAll(any(Specification.class), pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isZero();
        assertThat(pageable.getValue().getPageSize())
                .isEqualTo(TowerNpcEntryAdminService.MAX_PAGE_SIZE);
        assertThat(pageable.getValue().getSort().getOrderFor("id").getDirection())
                .isEqualTo(Sort.Direction.DESC);

        when(npcRepository.existsById(4L)).thenReturn(false);
        assertThatThrownBy(() -> service.list(4L, null, false, null, null, 0, 20))
                .isInstanceOf(TowerNpcException.class)
                .extracting("code")
                .isEqualTo(TowerNpcException.NOT_FOUND);
    }

    @Test
    @DisplayName("내보내기: 전체(최신 먼저), 이메일 포함")
    void export() {
        when(npcRepository.existsById(3L)).thenReturn(true);
        when(entryRepository.findAllByNpcIdOrderByIdDesc(3L)).thenReturn(List.of(entry()));
        List<TowerNpcEntryAdminResponse> all = service.export(3L);
        assertThat(all).hasSize(1);
        assertThat(all.get(0).email()).isEqualTo("a@b.c");
        assertThat(all.get(0).viewCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("숨기기/다시 공개: 그 NPC 의 글만 (다른 NPC 글 ID 는 150415)")
    void updateStatus() {
        TowerNpcEntry e = entry();
        when(entryRepository.findById(5L)).thenReturn(Optional.of(e));
        TowerNpcEntryAdminResponse hidden =
                service.updateStatus(3L, 5L, TowerNpcEntryStatus.HIDDEN);
        assertThat(hidden.status()).isEqualTo(TowerNpcEntryStatus.HIDDEN);
        assertThat(e.isVisible()).isFalse();
        assertThat(service.updateStatus(3L, 5L, TowerNpcEntryStatus.VISIBLE).status())
                .isEqualTo(TowerNpcEntryStatus.VISIBLE);

        assertThatThrownBy(() -> service.updateStatus(4L, 5L, TowerNpcEntryStatus.VISIBLE))
                .isInstanceOf(TowerNpcException.class)
                .extracting("code")
                .isEqualTo(TowerNpcException.ENTRY_NOT_FOUND);
    }

    @Test
    @DisplayName("시드 글: 운영자 글(is_seed, 작성자 없음), 길이는 설정 최대(없으면 500), 비었거나 넘으면 150412")
    void addSeed() {
        when(npcRepository.existsById(3L)).thenReturn(true);
        when(formRepository.findByNpcId(3L)).thenReturn(Optional.of(TowerTestFixtures.npcForm(3L)));
        when(entryRepository.save(any(TowerNpcEntry.class))).thenAnswer(inv -> inv.getArgument(0));

        TowerNpcEntryAdminResponse seed = service.addSeed(3L, " 첫 쪽지 ");
        assertThat(seed.isSeed()).isTrue();
        assertThat(seed.body()).isEqualTo("첫 쪽지");
        assertThat(seed.userId()).isNull();
        assertThat(seed.status()).isEqualTo(TowerNpcEntryStatus.VISIBLE);

        for (String bad : new String[] {null, " ", "가".repeat(101)}) {
            assertThatThrownBy(() -> service.addSeed(3L, bad))
                    .isInstanceOf(TowerNpcException.class)
                    .extracting("code")
                    .isEqualTo(TowerNpcException.ENTRY_BODY_INVALID);
        }

        when(npcRepository.existsById(4L)).thenReturn(true);
        when(formRepository.findByNpcId(4L)).thenReturn(Optional.empty());
        assertThat(service.addSeed(4L, "a".repeat(500)).body()).hasSize(500);
        assertThatThrownBy(() -> service.addSeed(4L, "a".repeat(501)))
                .isInstanceOf(TowerNpcException.class);
    }

    @Test
    @DisplayName("이메일 파기: 지운 행 수를 돌려준다 · NPC 없으면 150401")
    void purgeEmails() {
        when(npcRepository.existsById(3L)).thenReturn(true);
        when(entryRepository.purgeEmails(3L)).thenReturn(4);
        assertThat(service.purgeEmails(3L).purgedCount()).isEqualTo(4);

        when(npcRepository.existsById(4L)).thenReturn(false);
        assertThatThrownBy(() -> service.purgeEmails(4L)).isInstanceOf(TowerNpcException.class);
        verify(entryRepository).purgeEmails(any());
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    @DisplayName("필터 조건: NPC 는 항상, 상태·신고 있음·기간(KST → UTC)은 넘긴 것만 AND 로 묶는다")
    void specification() {
        Root root = mock(Root.class);
        CriteriaQuery query = mock(CriteriaQuery.class);
        CriteriaBuilder cb = mock(CriteriaBuilder.class);
        Path path = mock(Path.class);
        Predicate predicate = mock(Predicate.class);
        when(root.get(anyString())).thenReturn(path);
        when(cb.equal(any(Expression.class), any(Object.class))).thenReturn(predicate);
        when(cb.greaterThan(any(Expression.class), anyInt())).thenReturn(predicate);
        when(cb.greaterThanOrEqualTo(any(Expression.class), any(LocalDateTime.class)))
                .thenReturn(predicate);
        when(cb.lessThan(any(Expression.class), any(LocalDateTime.class))).thenReturn(predicate);
        when(cb.and(any(Predicate[].class))).thenReturn(predicate);

        LocalDate from = LocalDate.of(2026, 11, 1);
        LocalDate to = LocalDate.of(2026, 11, 8);
        Specification<TowerNpcEntry> full =
                TowerNpcEntryAdminService.specification(
                        3L, TowerNpcEntryStatus.HIDDEN, true, from, to);
        assertThat(full.toPredicate(root, query, cb)).isSameAs(predicate);
        verify(cb).equal(path, 3L);
        verify(cb).equal(path, TowerNpcEntryStatus.HIDDEN);
        verify(cb).greaterThan(path, 0);
        verify(cb).greaterThanOrEqualTo(path, LocalDateTime.of(2026, 10, 31, 15, 0));
        verify(cb).lessThan(path, LocalDateTime.of(2026, 11, 8, 15, 0));
        ArgumentCaptor<Predicate[]> all = ArgumentCaptor.forClass(Predicate[].class);
        verify(cb).and(all.capture());
        assertThat(all.getValue()).hasSize(5);

        CriteriaBuilder onlyNpc = mock(CriteriaBuilder.class);
        when(onlyNpc.equal(any(Expression.class), any(Object.class))).thenReturn(predicate);
        when(onlyNpc.and(any(Predicate[].class))).thenReturn(predicate);
        TowerNpcEntryAdminService.specification(3L, null, false, null, null)
                .toPredicate(root, query, onlyNpc);
        ArgumentCaptor<Predicate[]> one = ArgumentCaptor.forClass(Predicate[].class);
        verify(onlyNpc).and(one.capture());
        assertThat(one.getValue()).hasSize(1);
        verifyNoInteractions(query);
    }

    @Test
    @DisplayName("KST 날짜 0시는 UTC 전날 15시 · 페이지 크기 1~200")
    void helpers() {
        assertThat(TowerNpcEntryAdminService.kstStartUtc(LocalDate.of(2026, 11, 8)))
                .isEqualTo(LocalDateTime.of(2026, 11, 7, 15, 0));
        assertThat(TowerNpcEntryAdminService.clampSize(0)).isEqualTo(1);
        assertThat(TowerNpcEntryAdminService.clampSize(999)).isEqualTo(200);
        assertThat(TowerNpcEntryAdminService.clampSize(20)).isEqualTo(20);
    }
}
