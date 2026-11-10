package io.pinkspider.leveluptogethermvp.towerservice.application;

import io.pinkspider.global.facade.UserQueryFacade;
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
import io.pinkspider.leveluptogethermvp.towerservice.infrastructure.TowerNpcEntryRepository;
import io.pinkspider.leveluptogethermvp.towerservice.infrastructure.TowerNpcFormRepository;
import io.pinkspider.leveluptogethermvp.towerservice.infrastructure.TowerNpcRepository;
import jakarta.annotation.Nullable;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * LUT-760: NPC 에 글 남기기·공개 조회·신고 (유저 API).
 *
 * <ul>
 *   <li>제출 — 공개 NPC 이고 입력 받기가 켜져 있어야 한다(150401·150410). 로그인 전용이면 방문객은 150411(화면에서 막아도 서버가 다시 막는다).
 *       방문객은 입장 때 받은 {@code visitor_key}(세션)로 본인을 밝히고(없으면 150204) 채팅과 같은 기기·IP 제재·IP 빈도를 지난다. 본문은
 *       비거나 설정 최대 글자 수를 넘으면 150412, 이메일 받기면 형식·동의 150413. <b>같은 NPC 에는 하루(KST) 1회</b> — 로그인 유저는
 *       user_id, 방문객은 visitor_key 와 요청 IP 어느 쪽이든 오늘 글이 있으면 150414. 본문은 채팅과 같은 욕설 가림({@code **},
 *       HIGH 는 150305 거부)을 거쳐 저장한다.
 *   <li>공개 조회 — 공개(숨김 아님) 글을 <b>덜 보여 준 글부터</b>(노출 수 오름차순, 같은 수는 무작위) 최대 50개. 내려준 글은 노출 수 +1(정확한 집계일
 *       필요 없음). 공개 방식이 NONE 이거나 꺼졌으면 빈 목록. 이메일은 절대 내리지 않는다.
 *   <li>신고 — 로그인 유저·방문객 모두, 같은 사람의 재신고는 1번. 3건이면 자동 숨김(어드민이 다시 공개할 수 있다).
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(transactionManager = "towerTransactionManager", readOnly = true)
public class TowerNpcEntryService {

    static final ZoneId KST = ZoneId.of("Asia/Seoul");
    public static final int MAX_LIMIT = 50;
    static final int MAX_EMAIL_LENGTH = 254;
    static final int MAX_AUTHOR_NAME = 50;
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    /** {@code NOT IN ()} 은 SQL 오류라 제외할 글이 없을 때 넣는 센티널 */
    private static final List<Long> NO_EXCLUDE = List.of(-1L);

    private final TowerNpcRepository npcRepository;
    private final TowerNpcFormRepository formRepository;
    private final TowerNpcEntryRepository entryRepository;
    private final TowerChatModerationService moderationService;
    private final TowerVisitorSessionStore sessionStore;
    private final TowerVisitorGate visitorGate;
    @Lazy private final UserQueryFacade userQueryFacade;

    /** 입장 스냅샷용 — 켜진 설정만, 공개 글 수 포함. 조회 실패는 form 없이(스냅샷은 내려간다) */
    public Map<Long, TowerNpcFormView> formViews(Collection<Long> npcIds) {
        if (npcIds == null || npcIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, TowerNpcFormView> views = new HashMap<>();
        try {
            for (TowerNpcForm form : formRepository.findAllByNpcIdIn(npcIds)) {
                if (!form.isEnabled()) {
                    continue;
                }
                long count =
                        entryRepository.countByNpcIdAndStatus(
                                form.getNpcId(), TowerNpcEntryStatus.VISIBLE);
                views.put(form.getNpcId(), TowerNpcFormView.from(form, count));
            }
        } catch (RuntimeException e) {
            log.warn("[tower-npc] 입력 받기 설정 조회 실패 — form 없이 내린다: {}", e.getMessage());
            return Map.of();
        }
        return views;
    }

    @Transactional(transactionManager = "towerTransactionManager")
    public TowerNpcEntryView submit(
            Long npcId,
            @Nullable String userId,
            TowerNpcEntryRequest request,
            @Nullable String clientIp) {
        TowerNpc npc = findPublicNpc(npcId);
        TowerNpcForm form =
                formRepository
                        .findByNpcId(npcId)
                        .filter(TowerNpcForm::isEnabled)
                        .orElseThrow(TowerNpcException::formDisabled);

        TowerVisitorSession visitor = null;
        if (userId == null) {
            if (form.isLoginRequired()) {
                throw TowerNpcException.entryLoginRequired();
            }
            visitor = requireVisitor(request.visitorKey());
            visitorGate.checkChat(visitor.visitorKey(), clientIp);
        }

        String body = request.body() == null ? "" : request.body().trim();
        if (body.isEmpty() || body.codePointCount(0, body.length()) > form.getMaxLength()) {
            throw TowerNpcException.entryBodyInvalid();
        }

        LocalDateTime now = LocalDateTime.now();
        String email = null;
        LocalDateTime consentAt = null;
        if (form.isEmailEnabled()) {
            email = request.email() == null ? "" : request.email().trim();
            if (!Boolean.TRUE.equals(request.consent())
                    || email.length() > MAX_EMAIL_LENGTH
                    || !EMAIL.matcher(email).matches()) {
                throw TowerNpcException.entryEmailInvalid();
            }
            consentAt = now;
        }

        LocalDateTime dayStart = todayStartUtc();
        boolean already;
        if (userId != null) {
            already =
                    entryRepository.existsByNpcIdAndUserIdAndCreatedAtGreaterThanEqual(
                            npcId, userId, dayStart);
        } else {
            already =
                    entryRepository.existsByNpcIdAndVisitorKeyAndCreatedAtGreaterThanEqual(
                                    npcId, visitor.visitorKey(), dayStart)
                            || (clientIp != null
                                    && entryRepository
                                            .existsByNpcIdAndClientIpAndUserIdIsNullAndCreatedAtGreaterThanEqual(
                                                    npcId, clientIp, dayStart));
        }
        if (already) {
            throw TowerNpcException.entryDailyLimit();
        }

        ModerationResult moderation = moderationService.moderate(body);
        if (moderation.rejected()) {
            log.info(
                    "[tower-npc] 글 거절(금칙어): npcId={} user={} visitor={} words={}",
                    npcId,
                    userId,
                    visitor != null ? visitor.visitorNo() : null,
                    moderation.matchedWords());
            throw TowerChatException.profanityRejected();
        }

        TowerNpcEntry saved =
                entryRepository.save(
                        TowerNpcEntry.builder()
                                .npcId(npc.getId())
                                .userId(userId)
                                .visitorKey(visitor != null ? visitor.visitorKey() : null)
                                .visitorNo(visitor != null ? visitor.visitorNo() : null)
                                .clientIp(clientIp)
                                .authorName(userId != null ? nickname(userId) : null)
                                .body(moderation.content())
                                .email(email)
                                .consentAt(consentAt)
                                .build());
        log.info(
                "[tower-npc] 글 저장: entryId={} npcId={} user={} visitor={} email={} masked={}",
                saved.getId(),
                npcId,
                userId,
                saved.getVisitorNo(),
                email != null,
                moderation.masked());
        return TowerNpcEntryView.from(saved);
    }

    /** 공개 글 덜 보여 준 순으로 — 쓰기 트랜잭션인 이유는 노출 수 일괄 +1 */
    @Transactional(transactionManager = "towerTransactionManager")
    public TowerNpcEntryRandomResponse random(
            Long npcId, int limit, @Nullable Collection<Long> exclude) {
        findPublicNpc(npcId);
        TowerNpcForm form = formRepository.findByNpcId(npcId).orElse(null);
        if (form == null || !form.isDisplayed()) {
            return new TowerNpcEntryRandomResponse(List.of(), 0L);
        }
        int size = Math.max(1, Math.min(limit, MAX_LIMIT));
        Collection<Long> excluded = exclude == null || exclude.isEmpty() ? NO_EXCLUDE : exclude;
        List<TowerNpcEntry> picked = entryRepository.pickLeastShown(npcId, excluded, size);
        long total = entryRepository.countByNpcIdAndStatus(npcId, TowerNpcEntryStatus.VISIBLE);
        if (!picked.isEmpty()) {
            entryRepository.incrementViewCount(picked.stream().map(TowerNpcEntry::getId).toList());
        }
        return new TowerNpcEntryRandomResponse(
                picked.stream().map(TowerNpcEntryView::from).toList(), total);
    }

    @Transactional(transactionManager = "towerTransactionManager")
    public TowerNpcEntryReportResponse report(
            Long entryId, @Nullable String userId, @Nullable String visitorKey) {
        TowerNpcEntry entry =
                entryRepository.findById(entryId).orElseThrow(TowerNpcException::entryNotFound);
        String reporter =
                userId != null ? "u:" + userId : "v:" + requireVisitor(visitorKey).visitorKey();
        int inserted = entryRepository.insertReport(entryId, reporter);
        if (inserted == 0) {
            return new TowerNpcEntryReportResponse(true, !entry.isVisible());
        }
        boolean hiddenNow = entry.addReport();
        if (hiddenNow) {
            log.info(
                    "[tower-npc] 글 자동 숨김(신고 {}건): entryId={} npcId={}",
                    entry.getReportCount(),
                    entryId,
                    entry.getNpcId());
        }
        return new TowerNpcEntryReportResponse(false, !entry.isVisible());
    }

    private TowerNpc findPublicNpc(Long npcId) {
        return npcRepository
                .findById(npcId)
                .filter(TowerNpc::isPublicNpc)
                .orElseThrow(TowerNpcException::notFound);
    }

    /** 서버가 발급한 방문객 세션이어야 한다 — 모르는 키·빈 키는 150204 */
    private TowerVisitorSession requireVisitor(@Nullable String visitorKey) {
        if (visitorKey == null || visitorKey.isBlank()) {
            throw TowerEntryException.visitorKeyRequired();
        }
        return sessionStore
                .find(visitorKey.trim())
                .orElseThrow(TowerEntryException::visitorKeyRequired);
    }

    private String nickname(String userId) {
        try {
            String name = userQueryFacade.getUserNickname(userId);
            if (name == null) {
                return null;
            }
            return name.length() > MAX_AUTHOR_NAME ? name.substring(0, MAX_AUTHOR_NAME) : name;
        } catch (RuntimeException e) {
            log.warn("[tower-npc] 닉네임 조회 실패 — 이름 없이 저장: userId={} {}", userId, e.getMessage());
            return null;
        }
    }

    /** KST 오늘 0시 = UTC 저장 시각 기준 */
    static LocalDateTime todayStartUtc() {
        return LocalDate.now(KST)
                .atStartOfDay(KST)
                .withZoneSameInstant(ZoneOffset.UTC)
                .toLocalDateTime();
    }
}
