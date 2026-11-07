package io.pinkspider.leveluptogethermvp.towerservice.application;

import io.pinkspider.leveluptogethermvp.towerservice.application.exception.TowerNpcException;
import io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerAdminPage;
import io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerNpcEntryAdminResponse;
import io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerNpcEntryPurgeResponse;
import io.pinkspider.leveluptogethermvp.towerservice.domain.entity.TowerNpcEntry;
import io.pinkspider.leveluptogethermvp.towerservice.domain.entity.TowerNpcForm;
import io.pinkspider.leveluptogethermvp.towerservice.domain.enums.TowerNpcEntryStatus;
import io.pinkspider.leveluptogethermvp.towerservice.infrastructure.TowerNpcEntryRepository;
import io.pinkspider.leveluptogethermvp.towerservice.infrastructure.TowerNpcFormRepository;
import io.pinkspider.leveluptogethermvp.towerservice.infrastructure.TowerNpcRepository;
import jakarta.annotation.Nullable;
import jakarta.persistence.criteria.Predicate;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * LUT-760: 어드민 수집함(내부 API, admin-service 패스스루 LUT-761). 목록(상태·신고 있음·기간 KST)·내보내기(이메일 포함, CSV 는
 * admin-service 가 만들고 감사 로그를 남긴다)·숨기기/다시 공개·시드 글·이메일 일괄 파기(본문은 남긴다). NPC 는 비공개여도 다룬다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(transactionManager = "towerTransactionManager", readOnly = true)
public class TowerNpcEntryAdminService {

    static final ZoneId KST = ZoneId.of("Asia/Seoul");
    public static final int MAX_PAGE_SIZE = 200;
    static final int SEED_MAX_LENGTH = 500;

    private final TowerNpcRepository npcRepository;
    private final TowerNpcFormRepository formRepository;
    private final TowerNpcEntryRepository entryRepository;

    public TowerAdminPage<TowerNpcEntryAdminResponse> list(
            Long npcId,
            @Nullable TowerNpcEntryStatus status,
            boolean reportedOnly,
            @Nullable LocalDate from,
            @Nullable LocalDate to,
            int page,
            int size) {
        requireNpc(npcId);
        Page<TowerNpcEntry> result =
                entryRepository.findAll(
                        specification(npcId, status, reportedOnly, from, to),
                        PageRequest.of(
                                Math.max(0, page),
                                clampSize(size),
                                Sort.by(Sort.Direction.DESC, "id")));
        return TowerAdminPage.of(result.map(TowerNpcEntryAdminResponse::from));
    }

    /** 전체(최신 먼저) — CSV 내보내기용, 이메일 포함 */
    public List<TowerNpcEntryAdminResponse> export(Long npcId) {
        requireNpc(npcId);
        return entryRepository.findAllByNpcIdOrderByIdDesc(npcId).stream()
                .map(TowerNpcEntryAdminResponse::from)
                .toList();
    }

    @Transactional(transactionManager = "towerTransactionManager")
    public TowerNpcEntryAdminResponse updateStatus(
            Long npcId, Long entryId, TowerNpcEntryStatus status) {
        TowerNpcEntry entry = findEntry(npcId, entryId);
        entry.changeStatus(status);
        log.info("[tower-npc] 글 상태 변경: entryId={} npcId={} status={}", entryId, npcId, status);
        return TowerNpcEntryAdminResponse.from(entry);
    }

    /** 시드 글 — 운영자가 쓴 글(작성자 표시는 NPC 이름). 길이는 설정 최대 글자 수(없으면 500), 금칙어 가림은 하지 않는다 */
    @Transactional(transactionManager = "towerTransactionManager")
    public TowerNpcEntryAdminResponse addSeed(Long npcId, String rawBody) {
        requireNpc(npcId);
        int max =
                formRepository
                        .findByNpcId(npcId)
                        .map(TowerNpcForm::getMaxLength)
                        .orElse(SEED_MAX_LENGTH);
        String body = rawBody == null ? "" : rawBody.trim();
        if (body.isEmpty()
                || body.codePointCount(0, body.length()) > Math.min(max, SEED_MAX_LENGTH)) {
            throw TowerNpcException.entryBodyInvalid();
        }
        TowerNpcEntry saved =
                entryRepository.save(
                        TowerNpcEntry.builder().npcId(npcId).body(body).isSeed(true).build());
        log.info("[tower-npc] 시드 글 추가: entryId={} npcId={}", saved.getId(), npcId);
        return TowerNpcEntryAdminResponse.from(saved);
    }

    /** 이메일 일괄 파기 — 이벤트가 끝나면 운영자가 누른다. 본문은 남는다 */
    @Transactional(transactionManager = "towerTransactionManager")
    public TowerNpcEntryPurgeResponse purgeEmails(Long npcId) {
        requireNpc(npcId);
        int purged = entryRepository.purgeEmails(npcId);
        log.info("[tower-npc] 이메일 파기: npcId={} rows={}", npcId, purged);
        return new TowerNpcEntryPurgeResponse(purged);
    }

    static Specification<TowerNpcEntry> specification(
            Long npcId,
            @Nullable TowerNpcEntryStatus status,
            boolean reportedOnly,
            @Nullable LocalDate from,
            @Nullable LocalDate to) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("npcId"), npcId));
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (reportedOnly) {
                predicates.add(cb.greaterThan(root.get("reportCount"), 0));
            }
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), kstStartUtc(from)));
            }
            if (to != null) {
                predicates.add(cb.lessThan(root.get("createdAt"), kstStartUtc(to.plusDays(1))));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    /** KST 날짜 0시 → UTC 저장 시각 */
    static LocalDateTime kstStartUtc(LocalDate kstDate) {
        return kstDate.atStartOfDay(KST).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
    }

    static int clampSize(int size) {
        return Math.max(1, Math.min(size, MAX_PAGE_SIZE));
    }

    private void requireNpc(Long npcId) {
        if (!npcRepository.existsById(npcId)) {
            throw TowerNpcException.notFound();
        }
    }

    private TowerNpcEntry findEntry(Long npcId, Long entryId) {
        return entryRepository
                .findById(entryId)
                .filter(e -> npcId.equals(e.getNpcId()))
                .orElseThrow(TowerNpcException::entryNotFound);
    }
}
