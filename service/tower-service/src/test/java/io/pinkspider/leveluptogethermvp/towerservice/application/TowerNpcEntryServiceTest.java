package io.pinkspider.leveluptogethermvp.towerservice.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.pinkspider.global.facade.UserQueryFacade;
import io.pinkspider.leveluptogethermvp.profanity.domain.enums.ProfanitySeverity;
import io.pinkspider.leveluptogethermvp.towerservice.TowerTestFixtures;
import io.pinkspider.leveluptogethermvp.towerservice.application.chat.TowerChatModerationService;
import io.pinkspider.leveluptogethermvp.towerservice.application.chat.TowerChatModerationService.ModerationResult;
import io.pinkspider.leveluptogethermvp.towerservice.application.exception.TowerChatException;
import io.pinkspider.leveluptogethermvp.towerservice.application.exception.TowerEntryException;
import io.pinkspider.leveluptogethermvp.towerservice.application.exception.TowerNpcException;
import io.pinkspider.leveluptogethermvp.towerservice.application.visitor.TowerVisitorGate;
import io.pinkspider.leveluptogethermvp.towerservice.application.visitor.TowerVisitorSession;
import io.pinkspider.leveluptogethermvp.towerservice.application.visitor.TowerVisitorSessionStore;
import io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerNpcEntryRandomResponse;
import io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerNpcEntryReportResponse;
import io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerNpcEntryRequest;
import io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerNpcEntryView;
import io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerNpcFormView;
import io.pinkspider.leveluptogethermvp.towerservice.domain.entity.TowerNpc;
import io.pinkspider.leveluptogethermvp.towerservice.domain.entity.TowerNpcEntry;
import io.pinkspider.leveluptogethermvp.towerservice.domain.entity.TowerNpcForm;
import io.pinkspider.leveluptogethermvp.towerservice.domain.enums.TowerNpcEntryStatus;
import io.pinkspider.leveluptogethermvp.towerservice.domain.enums.TowerNpcFormBodyType;
import io.pinkspider.leveluptogethermvp.towerservice.domain.enums.TowerNpcFormDisplayMode;
import io.pinkspider.leveluptogethermvp.towerservice.infrastructure.TowerNpcEntryRepository;
import io.pinkspider.leveluptogethermvp.towerservice.infrastructure.TowerNpcFormRepository;
import io.pinkspider.leveluptogethermvp.towerservice.infrastructure.TowerNpcRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** LUT-760: NPC 글 남기기·공개 조회·신고 */
@ExtendWith(MockitoExtension.class)
class TowerNpcEntryServiceTest {

    @Mock private TowerNpcRepository npcRepository;
    @Mock private TowerNpcFormRepository formRepository;
    @Mock private TowerNpcEntryRepository entryRepository;
    @Mock private TowerChatModerationService moderationService;
    @Mock private TowerVisitorSessionStore sessionStore;
    @Mock private TowerVisitorGate visitorGate;
    @Mock private UserQueryFacade userQueryFacade;

    @InjectMocks private TowerNpcEntryService service;

    private final TowerNpc npc = TowerTestFixtures.npc(3L, 10L, true);
    private final TowerVisitorSession visitor =
            new TowerVisitorSession("k-1", 4821, "1.2.3.4", "dev-1", "UA", 0L);

    private void givenNpcWithForm(TowerNpcForm form) {
        when(npcRepository.findById(3L)).thenReturn(Optional.of(npc));
        when(formRepository.findByNpcId(3L)).thenReturn(Optional.ofNullable(form));
    }

    private static TowerNpcEntryRequest req(
            String body, String email, Boolean consent, String key) {
        return new TowerNpcEntryRequest(body, email, consent, key);
    }

    /** 3층 달 소원 — 여러 줄 200자, 이메일 받기(로그인 강제), 떠오르는 글 */
    private static TowerNpcForm emailForm() {
        return TowerNpcForm.builder()
                .id(103L)
                .npcId(3L)
                .enabled(true)
                .bodyType(TowerNpcFormBodyType.TEXTAREA)
                .maxLength(200)
                .submitLabel("소원 빌기")
                .submitLabelEn("Make a wish")
                .submitLabelJa("願う")
                .submitLabelAr("تمنى")
                .loginRequired(true)
                .emailEnabled(true)
                .consentText("이메일 수집 동의")
                .displayMode(TowerNpcFormDisplayMode.FLOAT)
                .displayMaskUrl("https://cdn.test/tower/mask/sky.png")
                .build();
    }

    private static TowerNpcEntry entry(long id, String body) {
        return TowerNpcEntry.builder().id(id).npcId(3L).body(body).build();
    }

    private void cleanModeration() {
        when(moderationService.moderate(anyString()))
                .thenAnswer(inv -> ModerationResult.clean(inv.getArgument(0)));
    }

    private void savesEntry() {
        when(entryRepository.save(any(TowerNpcEntry.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    @DisplayName("로그인 유저 제출: 공개 NPC·켜진 설정이면 닉네임 스냅샷과 함께 저장, 이메일 받기가 꺼져 있으면 이메일은 무시, 방문객 칸은 비운다")
    void submitUser() {
        givenNpcWithForm(TowerTestFixtures.npcForm(3L));
        when(entryRepository.existsByNpcIdAndUserIdAndCreatedAtGreaterThanEqual(
                        eq(3L), eq("u1"), any(LocalDateTime.class)))
                .thenReturn(false);
        cleanModeration();
        savesEntry();
        when(userQueryFacade.getUserNickname("u1")).thenReturn("닉네임");

        TowerNpcEntryView view =
                service.submit(3L, "u1", req("  트리야 안녕  ", "ignored@x.com", true, null), "9.9.9.9");

        ArgumentCaptor<TowerNpcEntry> saved = ArgumentCaptor.forClass(TowerNpcEntry.class);
        verify(entryRepository).save(saved.capture());
        assertThat(saved.getValue().getNpcId()).isEqualTo(3L);
        assertThat(saved.getValue().getUserId()).isEqualTo("u1");
        assertThat(saved.getValue().getBody()).isEqualTo("트리야 안녕");
        assertThat(saved.getValue().getEmail()).isNull();
        assertThat(saved.getValue().getConsentAt()).isNull();
        assertThat(saved.getValue().getVisitorKey()).isNull();
        assertThat(saved.getValue().getClientIp()).isEqualTo("9.9.9.9");
        assertThat(saved.getValue().isSeedEntry()).isFalse();
        assertThat(view.authorName()).isEqualTo("닉네임");
        assertThat(view.userId()).isEqualTo("u1");
        assertThat(view.visitorNo()).isNull();
        assertThat(view.visitorTag()).isNull();
        assertThat(view.isSeed()).isFalse();
        verifyNoInteractions(sessionStore, visitorGate);
    }

    @Test
    @DisplayName("방문객 제출: 세션 키로 본인 확인 → 채팅과 같은 제재·IP 빈도 → 욕설 가림 적용본 저장, 이름 번호·태그를 내린다")
    void submitVisitor() {
        givenNpcWithForm(TowerTestFixtures.npcForm(3L));
        when(sessionStore.find("k-1")).thenReturn(Optional.of(visitor));
        when(entryRepository.existsByNpcIdAndVisitorKeyAndCreatedAtGreaterThanEqual(
                        eq(3L), eq("k-1"), any(LocalDateTime.class)))
                .thenReturn(false);
        when(entryRepository.existsByNpcIdAndClientIpAndUserIdIsNullAndCreatedAtGreaterThanEqual(
                        eq(3L), eq("9.9.9.9"), any(LocalDateTime.class)))
                .thenReturn(false);
        when(moderationService.moderate("욕설 나무"))
                .thenReturn(
                        new ModerationResult(
                                "** 나무", true, false, ProfanitySeverity.LOW, List.of("욕설")));
        savesEntry();

        TowerNpcEntryView view =
                service.submit(3L, null, req("욕설 나무", null, null, " k-1 "), "9.9.9.9");

        verify(visitorGate).checkChat("k-1", "9.9.9.9");
        assertThat(view.body()).isEqualTo("** 나무");
        assertThat(view.visitorNo()).isEqualTo(4821);
        assertThat(view.visitorTag()).isNotBlank();
        assertThat(view.authorName()).isNull();
        assertThat(view.userId()).isNull();
        verifyNoInteractions(userQueryFacade);
    }

    @Test
    @DisplayName("방문객 키가 비었거나 서버가 모르는 키면 150204 (저장 없음)")
    void visitorKeyRequired() {
        givenNpcWithForm(TowerTestFixtures.npcForm(3L));
        assertThatThrownBy(() -> service.submit(3L, null, req("a", null, null, " "), null))
                .isInstanceOf(TowerEntryException.class)
                .extracting("code")
                .isEqualTo(TowerEntryException.VISITOR_KEY_REQUIRED);
        when(sessionStore.find("nope")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.submit(3L, null, req("a", null, null, "nope"), null))
                .isInstanceOf(TowerEntryException.class)
                .extracting("code")
                .isEqualTo(TowerEntryException.VISITOR_KEY_REQUIRED);
        verify(entryRepository, never()).save(any());
    }

    @Test
    @DisplayName("로그인 전용(이메일 받기면 강제) NPC 에 방문객이 제출하면 403 150411 — 세션 조회 전에 막는다")
    void loginRequired() {
        givenNpcWithForm(emailForm());
        assertThatThrownBy(() -> service.submit(3L, null, req("a", "a@b.c", true, "k-1"), null))
                .isInstanceOf(TowerNpcException.class)
                .extracting("code")
                .isEqualTo(TowerNpcException.ENTRY_LOGIN_REQUIRED);
        verifyNoInteractions(sessionStore, visitorGate);
        // 저장값 login_required=false 여도 이메일 받기면 로그인 필요
        TowerNpcForm lax =
                TowerNpcForm.builder()
                        .npcId(3L)
                        .enabled(true)
                        .emailEnabled(true)
                        .loginRequired(false)
                        .build();
        assertThat(lax.isLoginRequired()).isTrue();
    }

    @Test
    @DisplayName("본문이 비었거나 설정 최대 글자 수(코드 포인트)를 넘으면 150412 — 금칙어 검사 전에 막는다")
    void bodyInvalid() {
        givenNpcWithForm(TowerTestFixtures.npcForm(3L)); // max 100
        for (String body : new String[] {null, "   ", "가".repeat(101)}) {
            assertThatThrownBy(() -> service.submit(3L, "u1", req(body, null, null, null), null))
                    .isInstanceOf(TowerNpcException.class)
                    .extracting("code")
                    .isEqualTo(TowerNpcException.ENTRY_BODY_INVALID);
        }
        verify(entryRepository, never()).save(any());
        verifyNoInteractions(moderationService);
    }

    @Test
    @DisplayName("이메일 받기: 동의 안 함·형식 오류·너무 긴 이메일은 150413, 통과하면 이메일·동의 시각 저장(공백 정리)")
    void email() {
        givenNpcWithForm(emailForm());
        for (TowerNpcEntryRequest bad :
                List.of(
                        req("소원", "a@b.c", false, null),
                        req("소원", "a@b.c", null, null),
                        req("소원", "not-an-email", true, null),
                        req("소원", null, true, null),
                        req("소원", "a".repeat(250) + "@b.cd", true, null))) {
            assertThatThrownBy(() -> service.submit(3L, "u1", bad, null))
                    .isInstanceOf(TowerNpcException.class)
                    .extracting("code")
                    .isEqualTo(TowerNpcException.ENTRY_EMAIL_INVALID);
        }
        verify(entryRepository, never()).save(any());

        when(entryRepository.existsByNpcIdAndUserIdAndCreatedAtGreaterThanEqual(
                        eq(3L), eq("u1"), any(LocalDateTime.class)))
                .thenReturn(false);
        cleanModeration();
        savesEntry();
        when(userQueryFacade.getUserNickname("u1")).thenReturn(null);

        TowerNpcEntryView view =
                service.submit(3L, "u1", req("달님 소원", " Wish@Example.com ", true, null), null);

        ArgumentCaptor<TowerNpcEntry> saved = ArgumentCaptor.forClass(TowerNpcEntry.class);
        verify(entryRepository).save(saved.capture());
        assertThat(saved.getValue().getEmail()).isEqualTo("Wish@Example.com");
        assertThat(saved.getValue().getConsentAt()).isNotNull();
        assertThat(view.authorName()).isNull();
    }

    @Test
    @DisplayName(
            "같은 NPC 하루 1회: 로그인 유저는 user_id, 방문객은 visitor_key 또는 요청 IP 어느 쪽이든 오늘 글이 있으면 429 150414")
    void dailyLimit() {
        givenNpcWithForm(TowerTestFixtures.npcForm(3L));
        when(entryRepository.existsByNpcIdAndUserIdAndCreatedAtGreaterThanEqual(
                        eq(3L), eq("u1"), any(LocalDateTime.class)))
                .thenReturn(true);
        assertThatThrownBy(() -> service.submit(3L, "u1", req("또 쓰기", null, null, null), null))
                .isInstanceOf(TowerNpcException.class)
                .extracting("code")
                .isEqualTo(TowerNpcException.ENTRY_DAILY_LIMIT);

        when(sessionStore.find("k-1")).thenReturn(Optional.of(visitor));
        when(entryRepository.existsByNpcIdAndVisitorKeyAndCreatedAtGreaterThanEqual(
                        eq(3L), eq("k-1"), any(LocalDateTime.class)))
                .thenReturn(false);
        when(entryRepository.existsByNpcIdAndClientIpAndUserIdIsNullAndCreatedAtGreaterThanEqual(
                        eq(3L), eq("9.9.9.9"), any(LocalDateTime.class)))
                .thenReturn(true);
        assertThatThrownBy(() -> service.submit(3L, null, req("또", null, null, "k-1"), "9.9.9.9"))
                .isInstanceOf(TowerNpcException.class)
                .extracting("code")
                .isEqualTo(TowerNpcException.ENTRY_DAILY_LIMIT);
        verify(entryRepository, never()).save(any());
        verifyNoInteractions(moderationService);
    }

    @Test
    @DisplayName("HIGH 금칙어는 채팅과 같이 150305 거부 (저장 없음)")
    void profanityRejected() {
        givenNpcWithForm(TowerTestFixtures.npcForm(3L));
        when(entryRepository.existsByNpcIdAndUserIdAndCreatedAtGreaterThanEqual(
                        eq(3L), eq("u1"), any(LocalDateTime.class)))
                .thenReturn(false);
        when(moderationService.moderate("심한말"))
                .thenReturn(
                        new ModerationResult(
                                null, false, true, ProfanitySeverity.HIGH, List.of("심한말")));
        assertThatThrownBy(() -> service.submit(3L, "u1", req("심한말", null, null, null), null))
                .isInstanceOf(TowerChatException.class)
                .extracting("code")
                .isEqualTo(TowerChatException.PROFANITY_REJECTED);
        verify(entryRepository, never()).save(any());
    }

    @Test
    @DisplayName("비공개·없는 NPC 는 404 150401, 설정이 없거나 꺼졌으면 400 150410")
    void npcMissingOrFormDisabled() {
        when(npcRepository.findById(4L))
                .thenReturn(Optional.of(TowerTestFixtures.npc(4L, 10L, false)));
        assertThatThrownBy(() -> service.submit(4L, "u1", req("a", null, null, null), null))
                .isInstanceOf(TowerNpcException.class)
                .extracting("code")
                .isEqualTo(TowerNpcException.NOT_FOUND);
        when(npcRepository.findById(9L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.random(9L, 10, null))
                .isInstanceOf(TowerNpcException.class)
                .extracting("code")
                .isEqualTo(TowerNpcException.NOT_FOUND);

        when(npcRepository.findById(3L)).thenReturn(Optional.of(npc));
        when(formRepository.findByNpcId(3L))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(TowerNpcForm.builder().npcId(3L).enabled(false).build()));
        for (int i = 0; i < 2; i++) {
            assertThatThrownBy(() -> service.submit(3L, "u1", req("a", null, null, null), null))
                    .isInstanceOf(TowerNpcException.class)
                    .extracting("code")
                    .isEqualTo(TowerNpcException.FORM_DISABLED);
        }
        verifyNoInteractions(entryRepository);
    }

    @Test
    @DisplayName(
            "공개 조회: 덜 보여 준 글부터(limit 1~50 로 고정), 받은 ID 는 exclude, 내려준 글은 노출 수 +1, total 은 공개 글 수")
    void random() {
        when(npcRepository.findById(3L)).thenReturn(Optional.of(npc));
        when(formRepository.findByNpcId(3L)).thenReturn(Optional.of(TowerTestFixtures.npcForm(3L)));
        when(entryRepository.pickLeastShown(3L, List.of(-1L), 50))
                .thenReturn(List.of(entry(1L, "a"), entry(2L, "b")));
        when(entryRepository.countByNpcIdAndStatus(3L, TowerNpcEntryStatus.VISIBLE)).thenReturn(7L);

        TowerNpcEntryRandomResponse first = service.random(3L, 500, null);
        assertThat(first.entries()).extracting(TowerNpcEntryView::entryId).containsExactly(1L, 2L);
        assertThat(first.total()).isEqualTo(7L);
        verify(entryRepository).incrementViewCount(List.of(1L, 2L));

        when(entryRepository.pickLeastShown(3L, List.of(1L, 2L), 1)).thenReturn(List.of());
        TowerNpcEntryRandomResponse more = service.random(3L, 0, List.of(1L, 2L));
        assertThat(more.entries()).isEmpty();
        assertThat(more.total()).isEqualTo(7L);
        verify(entryRepository, times(1)).incrementViewCount(any());
    }

    @Test
    @DisplayName("공개 방식이 NONE 이거나 설정이 없으면 빈 목록·total 0 (글 조회 없음)")
    void randomNoneOrMissing() {
        when(npcRepository.findById(3L)).thenReturn(Optional.of(npc));
        when(formRepository.findByNpcId(3L))
                .thenReturn(
                        Optional.of(
                                TowerNpcForm.builder()
                                        .npcId(3L)
                                        .enabled(true)
                                        .displayMode(TowerNpcFormDisplayMode.NONE)
                                        .build()))
                .thenReturn(Optional.empty());
        for (int i = 0; i < 2; i++) {
            TowerNpcEntryRandomResponse res = service.random(3L, 50, List.of(5L));
            assertThat(res.entries()).isEmpty();
            assertThat(res.total()).isZero();
        }
        verifyNoInteractions(entryRepository);
    }

    @Test
    @DisplayName("신고: 로그인 u:·방문객 v: 단위로 1번씩 세고, 3건이면 자동 숨김 · 재신고는 duplicate")
    void report() {
        TowerNpcEntry e = entry(5L, "x");
        when(entryRepository.findById(5L)).thenReturn(Optional.of(e));
        when(entryRepository.insertReport(5L, "u:u1")).thenReturn(1);
        TowerNpcEntryReportResponse first = service.report(5L, "u1", null);
        assertThat(first.duplicate()).isFalse();
        assertThat(first.hidden()).isFalse();
        assertThat(e.getReportCount()).isEqualTo(1);

        when(entryRepository.insertReport(5L, "u:u1")).thenReturn(0);
        TowerNpcEntryReportResponse again = service.report(5L, "u1", null);
        assertThat(again.duplicate()).isTrue();
        assertThat(again.hidden()).isFalse();
        assertThat(e.getReportCount()).isEqualTo(1);

        TowerNpcEntry hot =
                TowerNpcEntry.builder().id(6L).npcId(3L).body("y").reportCount(2).build();
        when(entryRepository.findById(6L)).thenReturn(Optional.of(hot));
        when(sessionStore.find("k-1")).thenReturn(Optional.of(visitor));
        when(entryRepository.insertReport(6L, "v:k-1")).thenReturn(1);
        TowerNpcEntryReportResponse third = service.report(6L, null, "k-1");
        assertThat(third.duplicate()).isFalse();
        assertThat(third.hidden()).isTrue();
        assertThat(hot.isVisible()).isFalse();
        assertThat(hot.getStatus()).isEqualTo(TowerNpcEntryStatus.HIDDEN);

        // 이미 숨겨진 글에 또 신고 — 상태는 그대로 숨김
        when(entryRepository.insertReport(6L, "u:u9")).thenReturn(1);
        TowerNpcEntryReportResponse fourth = service.report(6L, "u9", null);
        assertThat(fourth.hidden()).isTrue();
        assertThat(hot.getReportCount()).isEqualTo(4);
    }

    @Test
    @DisplayName("신고 오류: 없는 글 404 150415 · 방문객 키 없음/모름 150204")
    void reportErrors() {
        when(entryRepository.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.report(99L, "u1", null))
                .isInstanceOf(TowerNpcException.class)
                .extracting("code")
                .isEqualTo(TowerNpcException.ENTRY_NOT_FOUND);

        when(entryRepository.findById(5L)).thenReturn(Optional.of(entry(5L, "x")));
        assertThatThrownBy(() -> service.report(5L, null, null))
                .isInstanceOf(TowerEntryException.class)
                .extracting("code")
                .isEqualTo(TowerEntryException.VISITOR_KEY_REQUIRED);
        when(sessionStore.find("zzz")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.report(5L, null, "zzz"))
                .isInstanceOf(TowerEntryException.class);
        verify(entryRepository, never()).insertReport(any(), any());
    }

    @Test
    @DisplayName("스냅샷용 formViews: 켜진 설정만 공개 글 수와 함께, 빈 입력·조회 실패는 빈 맵")
    void formViews() {
        TowerNpcForm off = TowerNpcForm.builder().npcId(2L).enabled(false).build();
        when(formRepository.findAllByNpcIdIn(List.of(1L, 2L)))
                .thenReturn(List.of(TowerTestFixtures.npcForm(1L), off));
        when(entryRepository.countByNpcIdAndStatus(1L, TowerNpcEntryStatus.VISIBLE)).thenReturn(5L);

        Map<Long, TowerNpcFormView> views = service.formViews(List.of(1L, 2L));
        assertThat(views).containsOnlyKeys(1L);
        assertThat(views.get(1L).entryCount()).isEqualTo(5L);
        assertThat(views.get(1L).displayMode()).isEqualTo(TowerNpcFormDisplayMode.TAG);
        assertThat(views.get(1L).submitLabelJa()).isEqualTo("保存");

        assertThat(service.formViews(List.of())).isEmpty();
        assertThat(service.formViews(null)).isEmpty();
        when(formRepository.findAllByNpcIdIn(List.of(9L))).thenThrow(new RuntimeException("db"));
        assertThat(service.formViews(List.of(9L))).isEmpty();
    }

    @Test
    @DisplayName("닉네임 조회가 실패하면 이름 없이 저장하고, 50자를 넘으면 자른다")
    void nicknameFallback() {
        givenNpcWithForm(TowerTestFixtures.npcForm(3L));
        when(entryRepository.existsByNpcIdAndUserIdAndCreatedAtGreaterThanEqual(
                        eq(3L), anyString(), any(LocalDateTime.class)))
                .thenReturn(false);
        cleanModeration();
        savesEntry();
        when(userQueryFacade.getUserNickname("u1")).thenThrow(new RuntimeException("down"));
        when(userQueryFacade.getUserNickname("u2")).thenReturn("a".repeat(60));

        assertThat(service.submit(3L, "u1", req("a", null, null, null), null).authorName())
                .isNull();
        assertThat(service.submit(3L, "u2", req("b", null, null, null), null).authorName())
                .hasSize(TowerNpcEntryService.MAX_AUTHOR_NAME);
    }

    @Test
    @DisplayName("하루 경계는 KST 0시(UTC 저장 시각으로 환산)")
    void todayStartUtc() {
        LocalDateTime expected =
                LocalDate.now(TowerNpcEntryService.KST)
                        .atStartOfDay(TowerNpcEntryService.KST)
                        .withZoneSameInstant(ZoneOffset.UTC)
                        .toLocalDateTime();
        assertThat(TowerNpcEntryService.todayStartUtc()).isEqualTo(expected);
        assertThat(expected.getHour()).isEqualTo(15);
    }
}
