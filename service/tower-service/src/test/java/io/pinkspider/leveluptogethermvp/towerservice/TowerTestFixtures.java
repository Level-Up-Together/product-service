package io.pinkspider.leveluptogethermvp.towerservice;

import io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerFloorAdminRequest;
import io.pinkspider.leveluptogethermvp.towerservice.domain.entity.TowerFloor;
import io.pinkspider.leveluptogethermvp.towerservice.domain.entity.TowerInstance;
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
                .bgmUrl(null)
                .instanceCapacity(30)
                .isPublic(isPublic)
                .build();
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
                null,
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
