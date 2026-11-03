package io.pinkspider.leveluptogethermvp.towerservice;

import io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerFloorAdminRequest;
import io.pinkspider.leveluptogethermvp.towerservice.domain.entity.TowerFloor;

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
}
