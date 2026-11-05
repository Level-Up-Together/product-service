package io.pinkspider.leveluptogethermvp.towerservice.presence;

import io.pinkspider.global.facade.GamificationQueryFacade;
import io.pinkspider.global.facade.UserQueryFacade;
import io.pinkspider.global.facade.dto.UserItemDto;
import io.pinkspider.global.facade.dto.UserProfileInfo;
import io.pinkspider.leveluptogethermvp.towerservice.application.TowerPresenceGateway;
import io.pinkspider.leveluptogethermvp.towerservice.application.visitor.TowerVisitorTag;
import io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerInstanceEvent;
import io.pinkspider.leveluptogethermvp.towerservice.domain.entity.TowerFloor;
import io.pinkspider.leveluptogethermvp.towerservice.domain.entity.TowerOccupant;
import io.pinkspider.leveluptogethermvp.towerservice.infrastructure.TowerFloorRepository;
import io.pinkspider.leveluptogethermvp.towerservice.infrastructure.TowerOccupantRepository;
import io.pinkspider.leveluptogethermvp.towerservice.realtime.TowerRealtimePublisher;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

/**
 * LUT-631: presence 의 단일 창구. 점유자 행이 생기면 {@link #register}, 지워지면 {@link #unregister}, 방을 옮기면 {@link
 * #moveInstance} — 호출 지점은 {@code TowerEntryService}(입장)·{@code TowerSeatService}(자동 착석)·{@code
 * TowerInstanceAllocator}(제거·자리 양보). 아바타 데이터(썸네일·장착 아이템·닉네임·레벨)는 등록 때 한 번 읽어 presence 에 넣어 두고 레벨 변경
 * 이벤트로 갱신한다.
 *
 * <p>이동은 목적지 좌표만 받아 층 마스크로 검증하고 밖이면 가장 가까운 안쪽으로 보정한 뒤 브로드캐스트한다. 재접속 때도 마지막 위치를 마스크로 재검증한다(마스크가 바뀌어
 * 밖이면 입장 지점).
 */
@Service
@Slf4j
public class TowerPresenceService implements TowerPresenceGateway {

    private final TowerPresenceStore store;
    private final TowerRealtimePublisher publisher;
    private final TowerFloorMaskCache maskCache;
    private final TowerFloorRepository floorRepository;
    private final TowerOccupantRepository occupantRepository;
    private final UserQueryFacade userQueryFacade;
    private final GamificationQueryFacade gamificationQueryFacade;
    private final int snapRadiusMaskPx;

    public TowerPresenceService(
            TowerPresenceStore store,
            TowerRealtimePublisher publisher,
            TowerFloorMaskCache maskCache,
            TowerFloorRepository floorRepository,
            TowerOccupantRepository occupantRepository,
            @Lazy UserQueryFacade userQueryFacade,
            @Lazy GamificationQueryFacade gamificationQueryFacade,
            @Value("${tower.presence.snap-radius-mask-px:150}") int snapRadiusMaskPx) {
        this.store = store;
        this.publisher = publisher;
        this.maskCache = maskCache;
        this.floorRepository = floorRepository;
        this.occupantRepository = occupantRepository;
        this.userQueryFacade = userQueryFacade;
        this.gamificationQueryFacade = gamificationQueryFacade;
        this.snapRadiusMaskPx = snapRadiusMaskPx;
    }

    /** 점유자 행이 생겼다 — presence 를 만들고 같은 방에 ENTER 를 보낸다 */
    public TowerPresence register(TowerOccupant occupant, boolean connected) {
        return register(occupant, connected, null);
    }

    /** LUT-749: 미션 중으로 들어오면(입장·자동 착석) 공개 미션 정보도 함께 둔다 — 스냅샷이 늦게 들어온 사람에게 미션명을 보여 준다 */
    public TowerPresence register(
            TowerOccupant occupant,
            boolean connected,
            @Nullable TowerInstanceEvent.MissionInfo mission) {
        LocalDateTime now = LocalDateTime.now();
        TowerPresence presence = build(occupant, connected, now, mission);
        store.save(presence);
        publisher.publishToInstance(
                occupant.getInstanceId(), TowerInstanceEvent.enter(presence.toView(), now));
        return presence;
    }

    /** 점유자 행이 지워진다 — presence 와 세션을 정리하고 LEAVE 를 보낸다 */
    public void unregister(TowerOccupant occupant) {
        store.delete(occupant.getInstanceId(), occupant.getId());
        store.unbindOccupant(occupant.getId());
        publisher.publishToInstance(
                occupant.getInstanceId(),
                TowerInstanceEvent.leave(
                        occupant.getId(), occupant.getUserId(), LocalDateTime.now()));
    }

    /** 자동 착석자 자리 양보 — 옛 방에 LEAVE, 새 방에 ENTER */
    public void moveInstance(TowerOccupant occupant, Long fromInstanceId, Long toInstanceId) {
        LocalDateTime now = LocalDateTime.now();
        TowerPresence presence =
                store.find(fromInstanceId, occupant.getId())
                        .map(p -> p.withInstance(toInstanceId))
                        .orElseGet(() -> build(occupant, false, now, null));
        store.delete(fromInstanceId, occupant.getId());
        store.save(presence);
        publisher.publishToInstance(
                fromInstanceId,
                TowerInstanceEvent.leave(occupant.getId(), occupant.getUserId(), now));
        publisher.publishToInstance(toInstanceId, TowerInstanceEvent.enter(presence.toView(), now));
    }

    /**
     * 웹소켓 join — 연결 표시 + 마지막 위치 복원(마스크 재검증, 밖이면 입장 지점). presence 가 없으면(Redis 유실) 다시 만든다. 복원 위치를
     * MOVE 로 알린다.
     */
    public TowerPresence join(TowerOccupant occupant) {
        LocalDateTime now = LocalDateTime.now();
        TowerPresence presence =
                store.find(occupant.getInstanceId(), occupant.getId())
                        .orElseGet(() -> build(occupant, true, now, null));
        WalkableMask.Point restored =
                restorePosition(occupant.getFloorNumber(), presence.x(), presence.y());
        presence = presence.withConnected(true, now).withPosition(restored.x(), restored.y(), now);
        store.save(presence);
        publisher.publishToInstance(
                occupant.getInstanceId(),
                TowerInstanceEvent.move(occupant.getId(), restored.x(), restored.y(), now));
        return presence;
    }

    /** 연결이 끊겼지만 자리는 남는 경우(미션 중) — 표시만 바꾼다, 브로드캐스트 없음 */
    public void markDisconnected(Long instanceId, Long occupantId) {
        store.find(instanceId, occupantId)
                .ifPresent(p -> store.save(p.withConnected(false, LocalDateTime.now())));
    }

    /** 이동 — 목적지만 받고 마스크로 검증·보정한 뒤 브로드캐스트. 보정된 좌표를 돌려준다(없으면 empty = 무시) */
    public Optional<WalkableMask.Point> move(Long instanceId, Long occupantId, int x, int y) {
        Optional<TowerPresence> found = store.find(instanceId, occupantId);
        if (found.isEmpty()) {
            return Optional.empty();
        }
        TowerPresence presence = found.get();
        WalkableMask.Point target = clamp(presence.floorNumber(), x, y);
        if (target == null) {
            return Optional.empty();
        }
        LocalDateTime now = LocalDateTime.now();
        store.save(presence.withPosition(target.x(), target.y(), now));
        publisher.publishToInstance(
                instanceId, TowerInstanceEvent.move(occupantId, target.x(), target.y(), now));
        return Optional.of(target);
    }

    /** LUT-749: 상태와 미션 정보(공개 미션만, 비공개·휴식이면 null)를 함께 바꾼다 */
    public void updateState(
            TowerOccupant occupant,
            String state,
            @Nullable TowerInstanceEvent.MissionInfo mission) {
        store.find(occupant.getInstanceId(), occupant.getId())
                .ifPresent(p -> store.save(p.withState(state, mission)));
    }

    /**
     * LUT-737: 아바타(닉네임·사진·레벨·장착 아이템)를 지금 값으로 다시 채운다 — 위치·연결·상태는 그대로. 같은 방에 ENTER 로 알린다(클라이언트는 이미 있는
     * 점유자면 아바타만 바꾸는 업서트). presence 가 없으면(Redis 유실) 새로 만든다. 방문객은 아바타 데이터가 없어 그대로 둔다.
     */
    public TowerPresence refreshAvatar(TowerOccupant occupant) {
        LocalDateTime now = LocalDateTime.now();
        Optional<TowerPresence> current = store.find(occupant.getInstanceId(), occupant.getId());
        if (occupant.getUserId() == null && current.isPresent()) {
            return current.get();
        }
        TowerPresence fresh = build(occupant, false, now, null);
        TowerPresence presence =
                current.map(
                                old ->
                                        old.withAvatar(
                                                fresh.nickname(),
                                                fresh.picture(),
                                                fresh.level(),
                                                fresh.items()))
                        .orElse(fresh);
        store.save(presence);
        publisher.publishToInstance(
                occupant.getInstanceId(), TowerInstanceEvent.enter(presence.toView(), now));
        return presence;
    }

    /** LUT-737: 장착 아이템이 바뀌었다 — 탑에 있으면(자리 유지·자동 착석 포함) 아바타를 다시 채워 같은 방에 알린다 */
    public boolean avatarChanged(String userId) {
        Optional<TowerOccupant> occupant = occupantRepository.findByUserId(userId);
        occupant.ifPresent(this::refreshAvatar);
        return occupant.isPresent();
    }

    /** 레벨이 올랐다 — 탑에 있으면 presence 갱신 + 같은 방에 LEVEL 브로드캐스트(머리 위 이름표) */
    public boolean levelChanged(String userId, int level) {
        Optional<TowerOccupant> occupant = occupantRepository.findByUserId(userId);
        if (occupant.isEmpty()) {
            return false;
        }
        Long instanceId = occupant.get().getInstanceId();
        store.find(instanceId, occupant.get().getId())
                .ifPresent(p -> store.save(p.withLevel(level)));
        publisher.publishToInstance(
                instanceId, TowerInstanceEvent.level(userId, level, LocalDateTime.now()));
        return true;
    }

    /** 입장 스냅샷 — 요청자와 차단 관계인 유저는 뺀다(서로 보이지 않는다) */
    public List<TowerPresenceView> snapshot(Long instanceId, @Nullable String requesterUserId) {
        List<TowerPresenceView> result = new ArrayList<>();
        for (TowerPresence presence : store.listByInstance(instanceId)) {
            if (requesterUserId != null
                    && presence.userId() != null
                    && !requesterUserId.equals(presence.userId())
                    && isBlocked(requesterUserId, presence.userId())) {
                continue;
            }
            result.add(presence.toView());
        }
        return result;
    }

    /** LUT-693: 점유자의 지금 위치(Redis presence) — 없으면 empty */
    public Optional<TowerPresence> find(TowerOccupant occupant) {
        return store.find(occupant.getInstanceId(), occupant.getId());
    }

    public List<TowerPresence> listRaw(Long instanceId) {
        return store.listByInstance(instanceId);
    }

    public void deleteOrphan(TowerPresence presence) {
        store.delete(presence.instanceId(), presence.occupantId());
        store.unbindOccupant(presence.occupantId());
        publisher.publishToInstance(
                presence.instanceId(),
                TowerInstanceEvent.leave(
                        presence.occupantId(), presence.userId(), LocalDateTime.now()));
    }

    @Override
    public boolean isConnected(String userId) {
        return occupantRepository
                .findByUserId(userId)
                .flatMap(o -> store.find(o.getInstanceId(), o.getId()))
                .map(TowerPresence::isConnected)
                .orElse(false);
    }

    private TowerPresence build(
            TowerOccupant occupant,
            boolean connected,
            LocalDateTime now,
            @Nullable TowerInstanceEvent.MissionInfo mission) {
        String nickname = null;
        String picture = null;
        Integer level = null;
        List<TowerAvatarItem> items = List.of();
        if (occupant.getUserId() != null) {
            try {
                UserProfileInfo profile = userQueryFacade.getUserProfile(occupant.getUserId());
                if (profile != null) {
                    nickname = profile.nickname();
                    picture = profile.picture();
                    level = profile.level();
                }
            } catch (Exception e) {
                log.warn(
                        "[tower] 프로필 조회 실패: userId={} error={}",
                        occupant.getUserId(),
                        e.getMessage());
            }
            try {
                Integer current = gamificationQueryFacade.getUserLevel(occupant.getUserId());
                if (current != null) {
                    level = current;
                }
            } catch (Exception e) {
                log.debug("[tower] 레벨 조회 실패 — 프로필 값 사용: userId={}", occupant.getUserId());
            }
            try {
                List<UserItemDto> equipped =
                        gamificationQueryFacade.getEquippedItemsByUserId(occupant.getUserId());
                if (equipped != null) {
                    items = equipped.stream().map(TowerAvatarItem::from).toList();
                }
            } catch (Exception e) {
                log.warn(
                        "[tower] 장착 아이템 조회 실패: userId={} error={}",
                        occupant.getUserId(),
                        e.getMessage());
            }
            if (level == null) {
                level = 1;
            }
        }
        return new TowerPresence(
                occupant.getId(),
                occupant.getInstanceId(),
                occupant.getFloorNumber(),
                occupant.getUserId(),
                occupant.getKind().name(),
                occupant.getVisitorNo(),
                nickname,
                picture,
                level,
                items,
                occupant.getSeatX(),
                occupant.getSeatY(),
                occupant.isMissionActive() ? TowerPresence.STATE_MISSION : TowerPresence.STATE_REST,
                connected,
                occupant.isAutoSeated(),
                now,
                TowerVisitorTag.of(occupant.getVisitorKey()),
                occupant.isMissionActive() ? mission : null);
    }

    private WalkableMask.Point restorePosition(int floorNumber, Integer x, Integer y) {
        Optional<TowerFloor> floor = floorRepository.findByFloorNumber(floorNumber);
        int px = x != null ? x : 0;
        int py = y != null ? y : 0;
        if (floor.isEmpty()) {
            return new WalkableMask.Point(px, py);
        }
        Optional<WalkableMask> mask = maskCache.get(floor.get());
        if (mask.isEmpty() || mask.get().isWalkable(px, py)) {
            return new WalkableMask.Point(px, py);
        }
        return new WalkableMask.Point(floor.get().getEntryPointX(), floor.get().getEntryPointY());
    }

    @Nullable
    private WalkableMask.Point clamp(int floorNumber, int x, int y) {
        Optional<TowerFloor> floor = floorRepository.findByFloorNumber(floorNumber);
        if (floor.isEmpty()) {
            return null;
        }
        Optional<WalkableMask> mask = maskCache.get(floor.get());
        if (mask.isEmpty()) {
            // 마스크를 못 읽으면 화면 밖만 막고 그대로 — 입장 지점·크기는 알 수 없다
            return x < 0 || y < 0 ? null : new WalkableMask.Point(x, y);
        }
        WalkableMask.Point snapped = mask.get().snapInside(x, y, snapRadiusMaskPx);
        if (snapped == null) {
            log.debug("[tower] 이동 보정 실패(너무 멀다) — 무시: floor={} x={} y={}", floorNumber, x, y);
        }
        return snapped;
    }

    private boolean isBlocked(String a, String b) {
        try {
            return userQueryFacade.isBlockedBetween(a, b);
        } catch (Exception e) {
            return false;
        }
    }
}
