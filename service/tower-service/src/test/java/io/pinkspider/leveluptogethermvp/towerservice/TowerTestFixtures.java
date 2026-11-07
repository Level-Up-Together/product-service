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
                null,
                null);
    }

    /** LUT-733: 창 이미지 + 상점 아이템 링크 + 버튼 문구(일부) + 기간 */
    public static io.pinkspider.leveluptogethermvp.towerservice.domain.entity.TowerNpcCard
            npcCard() {
        return io.pinkspider.leveluptogethermvp.towerservice.domain.entity.TowerNpcCard.builder()
                .imageUrl("https://cdn.test/tower/npc/card.png")
                .linkType(
                        io.pinkspider.leveluptogethermvp.towerservice.domain.enums
                                .TowerNpcCardLinkType.SHOP_ITEM)
                .linkTargetId(15L)
                .linkLabel("상점 가기")
                .linkLabelEn("Go to shop")
                .startsOn(java.time.LocalDate.of(2026, 11, 1))
                .endsOn(java.time.LocalDate.of(2026, 11, 30))
                .build();
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
                null,
                null,
                null,
                null,
                null);
    }

    /** LUT-760: 켜진 입력 받기 설정 (한 줄, 로그인 불필요, 태그 공개) */
    public static io.pinkspider.leveluptogethermvp.towerservice.domain.entity.TowerNpcForm npcForm(
            Long npcId) {
        return io.pinkspider.leveluptogethermvp.towerservice.domain.entity.TowerNpcForm.builder()
                .id(npcId + 100)
                .npcId(npcId)
                .enabled(true)
                .bodyType(
                        io.pinkspider.leveluptogethermvp.towerservice.domain.enums
                                .TowerNpcFormBodyType.INPUT)
                .placeholder("트리에 남길 한마디")
                .placeholderEn("A note for the tree")
                .maxLength(100)
                .submitLabel("저장")
                .submitLabelEn("Save")
                .submitLabelJa("保存")
                .submitLabelAr("حفظ")
                .loginRequired(false)
                .emailEnabled(false)
                .displayMode(
                        io.pinkspider.leveluptogethermvp.towerservice.domain.enums
                                .TowerNpcFormDisplayMode.TAG)
                .displayMaskUrl("https://cdn.test/tower/mask/tree.png")
                .build();
    }

    /** LUT-760: 입력 받기 설정 요청 DTO (켬·버튼 문구 4개 언어·공개 방식 TAG + 마스크) */
    public static io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerNpcFormDto
            npcFormDto(boolean emailEnabled) {
        return new io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerNpcFormDto(
                true,
                emailEnabled
                        ? io.pinkspider.leveluptogethermvp.towerservice.domain.enums
                                .TowerNpcFormBodyType.TEXTAREA
                        : io.pinkspider.leveluptogethermvp.towerservice.domain.enums
                                .TowerNpcFormBodyType.INPUT,
                "트리에 남길 한마디",
                "A note for the tree",
                null,
                null,
                emailEnabled ? 200 : 100,
                emailEnabled ? "소원 빌기" : "저장",
                emailEnabled ? "Make a wish" : "Save",
                emailEnabled ? "願う" : "保存",
                emailEnabled ? "تمنى" : "حفظ",
                false,
                emailEnabled,
                emailEnabled ? "이벤트 안내를 위한 이메일 수집에 동의해요 (이벤트 종료 후 파기)" : null,
                emailEnabled ? "I agree to email collection for event notices" : null,
                emailEnabled ? "イベント案内のためのメール収集に同意します" : null,
                emailEnabled ? "أوافق على جمع البريد الإلكتروني" : null,
                emailEnabled
                        ? io.pinkspider.leveluptogethermvp.towerservice.domain.enums
                                .TowerNpcFormDisplayMode.FLOAT
                        : io.pinkspider.leveluptogethermvp.towerservice.domain.enums
                                .TowerNpcFormDisplayMode.TAG,
                "https://cdn.test/tower/mask/tree.png");
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
                isPublic,
                null,
                null,
                null);
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
