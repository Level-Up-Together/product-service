package io.pinkspider.leveluptogethermvp.towerservice.application;

import io.pinkspider.global.facade.GamificationQueryFacade;
import io.pinkspider.leveluptogethermvp.towerservice.application.exception.TowerFloorException;
import io.pinkspider.leveluptogethermvp.towerservice.application.exception.TowerNpcException;
import io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerNpcAdminRequest;
import io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerNpcAdminResponse;
import io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerNpcCardDto;
import io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerNpcCardLinkDto;
import io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerNpcFormDto;
import io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerNpcLineDto;
import io.pinkspider.leveluptogethermvp.towerservice.domain.entity.TowerFloor;
import io.pinkspider.leveluptogethermvp.towerservice.domain.entity.TowerNpc;
import io.pinkspider.leveluptogethermvp.towerservice.domain.entity.TowerNpcForm;
import io.pinkspider.leveluptogethermvp.towerservice.domain.enums.TowerNpcFormDisplayMode;
import io.pinkspider.leveluptogethermvp.towerservice.infrastructure.TowerFloorRepository;
import io.pinkspider.leveluptogethermvp.towerservice.infrastructure.TowerNpcFormRepository;
import io.pinkspider.leveluptogethermvp.towerservice.infrastructure.TowerNpcRepository;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * LUT-693: 어드민 NPC CRUD (층마다 등록). 저장 규칙 — 층당 최대 3명(150402) · 자리는 배경 그림 안(150403, 못 읽으면 150109 —
 * LUT-746 부터 이동 불가 영역에도 둘 수 있다. 유저가 걸어와 NPC 를 가리지 않게 카운터 뒤·벽 앞에 둔다) · 대사 한 문장 160자 이내(LUT-751
 * {@link TowerNpcTextRule#fitsNpcLine}, 150404 — 예전엔 표시 폭 80). 수정은 다음 입장(스냅샷)부터 반영된다. 안 쓰게 된 프로필
 * 이미지는 커밋 뒤 지운다. LUT-733: 창 이미지·링크 — 상점 아이템은 판매 중(150405)·이벤트는 있는 것(150405), 앱 경로 규칙({@link
 * TowerNpcCardRule}, 150406), 기간 시작 ≤ 종료(150407). 안 쓰게 된 창 이미지도 지운다. LUT-760: 입력 받기 설정({@code
 * form}, NPC 당 1행) — 켜진 설정은 버튼 문구 4개 언어 필수, 이메일 받기면 동의 문구 4개 언어 필수 + 로그인 유저만 받기 강제, 공개 방식을 고르면 공개
 * 영역 마스크 필수(150408)이고 배경과 비율이 같아야 한다(150409, 못 읽으면 150109). 안 쓰게 된 공개 영역 마스크도 지운다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(transactionManager = "towerTransactionManager", readOnly = true)
public class TowerNpcAdminService {

    private final TowerFloorRepository towerFloorRepository;
    private final TowerNpcRepository towerNpcRepository;
    private final TowerNpcFormRepository towerNpcFormRepository;
    private final FloorMaskChecker floorMaskChecker;
    private final TowerStoredFileCleaner fileCleaner;
    private final GamificationQueryFacade gamificationQueryFacade;

    @Value("${tower.floor.mask-check.enabled:true}")
    private boolean maskCheckEnabled = true;

    public List<TowerNpcAdminResponse> list(Long floorId) {
        findFloor(floorId);
        List<TowerNpc> npcs = towerNpcRepository.findAllByFloorIdOrderBySortOrderAscIdAsc(floorId);
        Map<Long, TowerNpcForm> forms =
                towerNpcFormRepository
                        .findAllByNpcIdIn(npcs.stream().map(TowerNpc::getId).toList())
                        .stream()
                        .collect(Collectors.toMap(TowerNpcForm::getNpcId, f -> f, (a, b) -> a));
        return npcs.stream()
                .map(npc -> TowerNpcAdminResponse.from(npc, forms.get(npc.getId())))
                .toList();
    }

    @Transactional(transactionManager = "towerTransactionManager")
    public TowerNpcAdminResponse create(Long floorId, TowerNpcAdminRequest request) {
        TowerFloor floor = findFloor(floorId);
        if (towerNpcRepository.countByFloorId(floorId) >= TowerNpc.MAX_PER_FLOOR) {
            throw TowerNpcException.limitExceeded();
        }
        validate(floor, request);
        TowerNpc saved = towerNpcRepository.save(request.toEntity(floorId));
        TowerNpcForm form = null;
        if (request.form() != null) {
            form = towerNpcFormRepository.save(request.form().toEntity(saved.getId()));
        }
        log.info(
                "탑 NPC 등록: id={}, floor={}, public={}, form={}",
                saved.getId(),
                floor.getFloorNumber(),
                saved.isPublicNpc(),
                form != null && form.isEnabled());
        return TowerNpcAdminResponse.from(saved, form);
    }

    @Transactional(transactionManager = "towerTransactionManager")
    public TowerNpcAdminResponse update(Long floorId, Long npcId, TowerNpcAdminRequest request) {
        TowerFloor floor = findFloor(floorId);
        TowerNpc npc = findNpc(floorId, npcId);
        validate(floor, request);
        String previousImage = npc.getProfileImageUrl();
        String previousCardImage = cardImageOf(npc);
        TowerNpcForm form = towerNpcFormRepository.findByNpcId(npcId).orElse(null);
        String previousMask = form != null ? form.getDisplayMaskUrl() : null;
        npc.update(request.toEntity(floorId));
        npc.updateAutoBubble(request.autoBubble(), request.bubbleIntervalSeconds());
        if (request.card() != null) {
            npc.updateCard(request.card().toEntityOrNull());
        }
        if (request.form() != null) {
            TowerNpcForm source = request.form().toEntity(npcId);
            if (form == null) {
                form = towerNpcFormRepository.save(source);
            } else {
                form.update(source);
            }
        }
        String currentMask = form != null ? form.getDisplayMaskUrl() : null;
        fileCleaner.scheduleCleanup(
                TowerStoredFileCleaner.urls(previousImage, previousCardImage, previousMask),
                TowerStoredFileCleaner.urls(
                        npc.getProfileImageUrl(), cardImageOf(npc), currentMask));
        log.info(
                "탑 NPC 수정: id={}, floor={}, public={}, form={}",
                npcId,
                floor.getFloorNumber(),
                npc.isPublicNpc(),
                form != null && form.isEnabled());
        return TowerNpcAdminResponse.from(npc, form);
    }

    @Transactional(transactionManager = "towerTransactionManager")
    public void delete(Long floorId, Long npcId) {
        TowerNpc npc = findNpc(floorId, npcId);
        String mask =
                towerNpcFormRepository
                        .findByNpcId(npcId)
                        .map(TowerNpcForm::getDisplayMaskUrl)
                        .orElse(null);
        towerNpcRepository.delete(npc); // 설정·글은 DB ON DELETE CASCADE
        fileCleaner.scheduleCleanup(
                TowerStoredFileCleaner.urls(npc.getProfileImageUrl(), cardImageOf(npc), mask),
                Set.of());
        log.info("탑 NPC 삭제: id={}, floorId={}", npcId, floorId);
    }

    private void validate(TowerFloor floor, TowerNpcAdminRequest request) {
        for (TowerNpcLineDto line : request.lines()) {
            if (!TowerNpcTextRule.fitsNpcLine(line.text())
                    || !TowerNpcTextRule.fitsNpcLine(line.textEn())
                    || !TowerNpcTextRule.fitsNpcLine(line.textJa())
                    || !TowerNpcTextRule.fitsNpcLine(line.textAr())) {
                throw TowerNpcException.lineTooLong();
            }
        }
        validateSeat(floor, request.seatX(), request.seatY());
        validateCard(request.card());
        validateForm(floor, request.form());
    }

    /**
     * LUT-760: 켜진 설정만 본다 — 버튼 문구 4개 언어, 이메일 받기면 동의 문구 4개 언어, 공개 방식(TAG·FLOAT)이면 공개 영역 마스크(배경과 같은
     * 비율). 꺼진 설정은 값만 저장한다(다시 켤 때 검사).
     */
    private void validateForm(TowerFloor floor, TowerNpcFormDto form) {
        if (form == null || !form.isEnabled()) {
            return;
        }
        if (isBlank(form.submitLabel())
                || isBlank(form.submitLabelEn())
                || isBlank(form.submitLabelJa())
                || isBlank(form.submitLabelAr())) {
            throw TowerNpcException.formInvalid();
        }
        if (form.isEmailEnabled()
                && (isBlank(form.consentText())
                        || isBlank(form.consentTextEn())
                        || isBlank(form.consentTextJa())
                        || isBlank(form.consentTextAr()))) {
            throw TowerNpcException.formInvalid();
        }
        if (form.displayModeOrNone() != TowerNpcFormDisplayMode.NONE) {
            if (isBlank(form.displayMaskUrl())) {
                throw TowerNpcException.formInvalid();
            }
            validateDisplayMask(floor, form.displayMaskUrl().trim());
        }
    }

    /** 공개 영역 마스크는 앞 가림 마스크(LUT-725)와 같은 규칙 — 배경과 비율이 같아야 한다 */
    private void validateDisplayMask(TowerFloor floor, String maskUrl) {
        if (!maskCheckEnabled) {
            log.warn(
                    "탑 NPC 공개 영역 마스크 비율 검사가 꺼져 있다(tower.floor.mask-check.enabled=false): floor={}",
                    floor.getFloorNumber());
            return;
        }
        boolean same;
        try {
            same = floorMaskChecker.sameAspectRatio(maskUrl, floor.getBackgroundImageUrl());
        } catch (IOException | RuntimeException e) {
            log.warn(
                    "탑 NPC 공개 영역 마스크를 읽지 못함: floor={}, mask={}, error={}",
                    floor.getFloorNumber(),
                    maskUrl,
                    e.getMessage());
            throw TowerFloorException.maskUnreadable();
        }
        if (!same) {
            throw TowerNpcException.formMaskAspectMismatch();
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /** LUT-733: 창 이미지·링크 — 이미지도 링크도 없는 묶음은 저장하지 않는다(toEntityOrNull) */
    private void validateCard(TowerNpcCardDto card) {
        if (card == null) {
            return;
        }
        if (card.startsOn() != null
                && card.endsOn() != null
                && card.startsOn().isAfter(card.endsOn())) {
            throw TowerNpcException.cardPeriodInvalid();
        }
        TowerNpcCardLinkDto link = card.link();
        if (link == null || link.type() == null) {
            return;
        }
        switch (link.type()) {
            case SHOP_ITEM -> {
                Long id = requireTarget(link);
                if (!gamificationQueryFacade.findOnSaleShopItemIds(List.of(id)).contains(id)) {
                    throw TowerNpcException.cardLinkTargetMissing();
                }
            }
            case EVENT -> {
                Long id = requireTarget(link);
                if (!gamificationQueryFacade.findExistingEventIds(List.of(id)).contains(id)) {
                    throw TowerNpcException.cardLinkTargetMissing();
                }
            }
            case PATH -> {
                if (!TowerNpcCardRule.isAppPath(link.path() == null ? null : link.path().trim())) {
                    throw TowerNpcException.cardLinkInvalid();
                }
            }
            case SUBSCRIPTION -> {
                // 대상 없음 — 클라이언트가 멤버십 화면으로 보낸다
            }
        }
    }

    private static Long requireTarget(TowerNpcCardLinkDto link) {
        if (link.targetId() == null || link.targetId() <= 0) {
            throw TowerNpcException.cardLinkInvalid();
        }
        return link.targetId();
    }

    private static String cardImageOf(TowerNpc npc) {
        return npc.getCard() != null ? npc.getCard().getImageUrl() : null;
    }

    /** LUT-746: 자리는 배경 그림 안이기만 하면 된다 — 바닥 마스크(이동 가능 영역)는 보지 않는다. 음수는 요청 검증(@Min 0)이 먼저 막는다 */
    private void validateSeat(TowerFloor floor, int x, int y) {
        if (!maskCheckEnabled) {
            log.warn(
                    "탑 NPC 자리 배경 범위 검사가 꺼져 있다(tower.floor.mask-check.enabled=false): floor={}",
                    floor.getFloorNumber());
            return;
        }
        boolean inside;
        try {
            inside = floorMaskChecker.isInsideImage(floor.getBackgroundImageUrl(), x, y);
        } catch (IOException | RuntimeException e) {
            log.warn(
                    "탑 NPC 자리 검사 중 배경을 읽지 못함: floor={}, background={}, error={}",
                    floor.getFloorNumber(),
                    floor.getBackgroundImageUrl(),
                    e.getMessage());
            throw TowerFloorException.backgroundUnreadable();
        }
        if (!inside) {
            throw TowerNpcException.seatOutsideBackground();
        }
    }

    private TowerFloor findFloor(Long floorId) {
        return towerFloorRepository.findById(floorId).orElseThrow(TowerFloorException::notFound);
    }

    private TowerNpc findNpc(Long floorId, Long npcId) {
        return towerNpcRepository
                .findByIdAndFloorId(npcId, floorId)
                .orElseThrow(TowerNpcException::notFound);
    }
}
