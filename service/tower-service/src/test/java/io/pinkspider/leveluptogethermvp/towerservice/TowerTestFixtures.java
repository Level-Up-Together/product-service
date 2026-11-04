package io.pinkspider.leveluptogethermvp.towerservice;

import io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerFloorAdminRequest;
import io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerNpcAdminRequest;
import io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerNpcLineDto;
import io.pinkspider.leveluptogethermvp.towerservice.domain.entity.TowerFloor;
import io.pinkspider.leveluptogethermvp.towerservice.domain.entity.TowerInstance;
import io.pinkspider.leveluptogethermvp.towerservice.domain.entity.TowerNpc;
import io.pinkspider.leveluptogethermvp.towerservice.domain.entity.TowerNpcLine;
import io.pinkspider.leveluptogethermvp.towerservice.domain.entity.TowerOccupant;
import io.pinkspider.leveluptogethermvp.towerservice.domain.enums.TowerOccupantKind;
import java.time.LocalDateTime;

/** LUT-629 테스트 공용 층 생성 헬퍼 */
public final class TowerTestFixtures {

    private TowerTestFixtures() {}

    public static TowerFloor floor(
            Long id, int number, Integer entryLevel, boolean guest, boolean isPublic) {
        return TowerFloor.builder()
                .id(id)
                .floorNumber(number)
                .name(number + "층")
                .nameEn("Floor " + number)
                .nameJa(number + "階")
                .nameAr("الطابق " + number)
                .entryLevel(entryLevel)
                .guestAllowed(guest)
                .backgroundImageUrl("https://cdn.test/tower/bg-" + number + ".jpg")
                .floorMaskUrl("https://cdn.test/tower/mask-" + number + ".png")
                .entryPointX(100)
                .entryPointY(200)
                // LUT-637: 공개 층은 배경 음악 필수
                .bgmUrl(isPublic ? "https://cdn.test/tower/bgm-" + number + ".mp3" : null)
                .instanceCapacity(30)
                .isPublic(isPublic)
                .build();
    }

    /** LUT-693: NPC (대사 2개) */
    public static TowerNpc npc(Long id, Long floorId, boolean isPublic) {
        return TowerNpc.builder()
                .id(id)
                .floorId(floorId)
                .name("탑지기")
                .nameEn("Keeper")
                .nameJa("塔守")
                .nameAr("الحارس")
                .profileImageUrl("https://cdn.test/tower/npc/" + id + ".png")
                .statusText("조는 중")
                .seatX(120)
                .seatY(220)
                .isPublic(isPublic)
                .sortOrder(0)
                .lines(
                        new java.util.ArrayList<>(
                                java.util.List.of(
                                        new TowerNpcLine("어서 와", "Welcome", "ようこそ", "مرحبا"),
                                        new TowerNpcLine(
                                                "미션 해 볼래?", "Try a mission?", "ミッション?", "مهمة؟"))))
                .build();
    }

    /** LUT-693: 점유자의 presence(위치만 의미 있음) */
    public static io.pinkspider.leveluptogethermvp.towerservice.presence.TowerPresence presence(
            TowerOccupant occupant, int x, int y) {
        return new io.pinkspider.leveluptogethermvp.towerservice.presence.TowerPresence(
                occupant.getId(),
                occupant.getInstanceId(),
                occupant.getFloorNumber(),
                occupant.getUserId(),
                "DIRECT",
                null,
                "닉",
                null,
                1,
                java.util.List.of(),
                x,
                y,
                "MISSION",
                true,
                false,
                java.time.LocalDateTime.now(),
                null);
    }

    /** LUT-693: NPC 등록 요청 */
    public static TowerNpcAdminRequest npcRequest(String imageUrl, int x, int y, String line) {
        return new TowerNpcAdminRequest(
                "탑지기",
                "Keeper",
                "塔守",
                "الحارس",
                imageUrl,
                "조는 중",
                " ",
                null,
                null,
                x,
                y,
                java.util.List.of(new TowerNpcLineDto(line, "Welcome", "ようこそ", "مرحبا")),
                true,
                null);
    }

    public static TowerFloorAdminRequest request(
            int number, Integer entryLevel, boolean guest, boolean isPublic) {
        return new TowerFloorAdminRequest(
                number,
                number + "층",
                "Floor " + number,
                number + "階",
                "الطابق " + number,
                entryLevel,
                guest,
                "https://cdn.test/tower/bg-" + number + ".jpg",
                "https://cdn.test/tower/mask-" + number + ".png",
                100,
                200,
                isPublic ? "https://cdn.test/tower/bgm-" + number + ".mp3" : null,
                null,
                isPublic);
    }

    public static TowerInstance instance(Long id, int floorNumber) {
        return TowerInstance.builder()
                .id(id)
                .floorNumber(floorNumber)
                .openedAt(LocalDateTime.of(2026, 11, 3, 12, 0))
                .build();
    }

    public static TowerOccupant occupant(
            Long id,
            Long instanceId,
            int floorNumber,
            String userId,
            TowerOccupantKind kind,
            boolean missionActive) {
        LocalDateTime now = LocalDateTime.of(2026, 11, 3, 12, 0);
        return TowerOccupant.builder()
                .id(id)
                .instanceId(instanceId)
                .floorNumber(floorNumber)
                .userId(kind == TowerOccupantKind.VISITOR ? null : userId)
                .visitorKey(kind == TowerOccupantKind.VISITOR ? userId : null)
                .kind(kind)
                .missionActive(missionActive)
                .seatX(100)
                .seatY(200)
                .enteredAt(now)
                .lastActiveAt(now)
                .build();
    }
}
