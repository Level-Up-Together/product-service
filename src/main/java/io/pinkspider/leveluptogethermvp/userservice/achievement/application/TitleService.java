package io.pinkspider.leveluptogethermvp.userservice.achievement.application;

import io.pinkspider.global.event.TitleAcquiredEvent;
import io.pinkspider.leveluptogethermvp.userservice.achievement.domain.dto.TitleResponse;
import io.pinkspider.leveluptogethermvp.userservice.achievement.domain.dto.UserTitleResponse;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.entity.Title;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.entity.UserTitle;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.enums.TitleAcquisitionType;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.enums.TitlePosition;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.enums.TitleRarity;
import io.pinkspider.leveluptogethermvp.gamificationservice.infrastructure.TitleRepository;
import io.pinkspider.leveluptogethermvp.gamificationservice.infrastructure.UserTitleRepository;
import io.pinkspider.leveluptogethermvp.feedservice.infrastructure.ActivityFeedRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TitleService {

    private final TitleRepository titleRepository;
    private final UserTitleRepository userTitleRepository;
    private final ActivityFeedRepository activityFeedRepository;
    private final ApplicationEventPublisher eventPublisher;

    // 전체 칭호 목록
    public List<TitleResponse> getAllTitles() {
        return titleRepository.findByIsActiveTrue().stream()
            .map(TitleResponse::from)
            .toList();
    }

    // 포지션별 칭호 목록 (LEFT 또는 RIGHT)
    public List<TitleResponse> getTitlesByPosition(TitlePosition position) {
        return titleRepository.findByPositionTypeAndIsActiveTrue(position).stream()
            .map(TitleResponse::from)
            .toList();
    }

    public List<TitleResponse> getTitlesByRarity(TitleRarity rarity) {
        return titleRepository.findByRarityAndIsActiveTrue(rarity).stream()
            .map(TitleResponse::from)
            .toList();
    }

    // 유저의 칭호 목록
    public List<UserTitleResponse> getUserTitles(String userId) {
        return userTitleRepository.findByUserIdWithTitle(userId).stream()
            .map(UserTitleResponse::from)
            .toList();
    }

    // 유저의 포지션별 칭호 목록
    public List<UserTitleResponse> getUserTitlesByPosition(String userId, TitlePosition position) {
        return userTitleRepository.findByUserIdWithTitle(userId).stream()
            .filter(ut -> position.equals(ut.getTitle().getPositionType()))
            .map(UserTitleResponse::from)
            .toList();
    }

    // 장착된 칭호 목록 조회 (LEFT와 RIGHT 모두)
    public List<UserTitleResponse> getEquippedTitles(String userId) {
        return userTitleRepository.findEquippedTitlesByUserId(userId).stream()
            .map(UserTitleResponse::from)
            .toList();
    }

    // 장착된 칭호의 조합된 이름 반환 (예: "신입 모험가")
    public String getCombinedEquippedTitleName(String userId) {
        return getCombinedEquippedTitleInfo(userId).name();
    }

    /**
     * 장착된 칭호의 조합된 정보 반환 (이름과 가장 높은 등급)
     */
    public TitleInfo getCombinedEquippedTitleInfo(String userId) {
        List<UserTitle> equippedTitles = userTitleRepository.findEquippedTitlesByUserId(userId);
        if (equippedTitles.isEmpty()) {
            return new TitleInfo(null, null);
        }

        UserTitle leftUserTitle = equippedTitles.stream()
            .filter(ut -> ut.getEquippedPosition() == TitlePosition.LEFT)
            .findFirst()
            .orElse(null);

        UserTitle rightUserTitle = equippedTitles.stream()
            .filter(ut -> ut.getEquippedPosition() == TitlePosition.RIGHT)
            .findFirst()
            .orElse(null);

        String leftTitle = leftUserTitle != null ? leftUserTitle.getTitle().getDisplayName() : null;
        String rightTitle = rightUserTitle != null ? rightUserTitle.getTitle().getDisplayName() : null;

        // 가장 높은 등급 선택
        TitleRarity highestRarity = getHighestRarity(
            leftUserTitle != null ? leftUserTitle.getTitle().getRarity() : null,
            rightUserTitle != null ? rightUserTitle.getTitle().getRarity() : null
        );

        String combinedTitle;
        if (leftTitle != null && rightTitle != null) {
            combinedTitle = leftTitle + " " + rightTitle;
        } else if (leftTitle != null) {
            combinedTitle = leftTitle;
        } else {
            combinedTitle = rightTitle;
        }

        return new TitleInfo(combinedTitle, highestRarity);
    }

    /**
     * 두 등급 중 더 높은 등급 반환
     */
    private TitleRarity getHighestRarity(TitleRarity r1, TitleRarity r2) {
        if (r1 == null) return r2;
        if (r2 == null) return r1;
        return r1.ordinal() > r2.ordinal() ? r1 : r2;
    }

    /**
     * 칭호 정보 (이름과 등급)
     */
    public record TitleInfo(String name, TitleRarity rarity) {}

    // 포지션별 장착된 칭호 조회
    public Optional<UserTitleResponse> getEquippedTitleByPosition(String userId, TitlePosition position) {
        return userTitleRepository.findEquippedByUserIdAndPosition(userId, position)
            .map(UserTitleResponse::from);
    }

    // 칭호 부여
    @Transactional
    public UserTitleResponse grantTitle(String userId, Long titleId) {
        return grantTitle(userId, titleId, true);
    }

    // 칭호 부여 (알림 여부 선택)
    @Transactional
    public UserTitleResponse grantTitle(String userId, Long titleId, boolean notify) {
        if (userTitleRepository.existsByUserIdAndTitleId(userId, titleId)) {
            log.debug("이미 보유한 칭호: userId={}, titleId={}", userId, titleId);
            return userTitleRepository.findByUserIdAndTitleId(userId, titleId)
                .map(UserTitleResponse::from)
                .orElse(null);
        }

        Title title = titleRepository.findById(titleId)
            .orElseThrow(() -> new IllegalArgumentException("칭호를 찾을 수 없습니다: " + titleId));

        UserTitle userTitle = UserTitle.builder()
            .userId(userId)
            .title(title)
            .acquiredAt(LocalDateTime.now())
            .build();

        UserTitle saved = userTitleRepository.save(userTitle);
        log.info("칭호 획득: userId={}, title={}, position={}", userId, title.getName(), title.getPositionType());

        // 이벤트 발행 (신규 획득 시에만) - 트랜잭션 커밋 후 비동기로 알림 생성
        if (notify) {
            eventPublisher.publishEvent(new TitleAcquiredEvent(
                userId,
                title.getId(),
                title.getDisplayName(),
                title.getRarity().name()
            ));
        }

        return UserTitleResponse.from(saved);
    }

    // 칭호 장착 (포지션별로 장착)
    @Transactional
    public UserTitleResponse equipTitle(String userId, Long titleId) {
        UserTitle userTitle = userTitleRepository.findByUserIdAndTitleId(userId, titleId)
            .orElseThrow(() -> new IllegalArgumentException("보유하지 않은 칭호입니다."));

        TitlePosition position = userTitle.getTitle().getPositionType();

        // 같은 포지션의 기존 장착 해제
        userTitleRepository.unequipByUserIdAndPosition(userId, position);

        // 새 칭호 장착
        userTitle.equip(position);
        log.info("칭호 장착: userId={}, title={}, position={}", userId, userTitle.getTitle().getName(), position);

        // 피드의 칭호도 업데이트
        updateUserFeedsTitle(userId);

        return UserTitleResponse.from(userTitle);
    }

    // 특정 포지션 칭호 해제
    @Transactional
    public void unequipTitle(String userId, TitlePosition position) {
        userTitleRepository.unequipByUserIdAndPosition(userId, position);
        log.info("칭호 해제: userId={}, position={}", userId, position);

        // 피드의 칭호도 업데이트
        updateUserFeedsTitle(userId);
    }

    // 모든 칭호 해제
    @Transactional
    public void unequipAllTitles(String userId) {
        userTitleRepository.unequipAllByUserId(userId);
        log.info("모든 칭호 해제: userId={}", userId);

        // 피드의 칭호도 업데이트
        updateUserFeedsTitle(userId);
    }

    // 사용자의 모든 피드의 칭호 업데이트
    private void updateUserFeedsTitle(String userId) {
        TitleInfo titleInfo = getCombinedEquippedTitleInfo(userId);
        int updatedCount = activityFeedRepository.updateUserTitleByUserId(userId, titleInfo.name(), titleInfo.rarity());
        log.info("피드 칭호 업데이트: userId={}, title={}, rarity={}, count={}",
            userId, titleInfo.name(), titleInfo.rarity(), updatedCount);
    }

    // 칭호 생성 (관리자용)
    @Transactional
    public TitleResponse createTitle(String name, String description, TitleRarity rarity,
                                      TitlePosition positionType, TitleAcquisitionType acquisitionType,
                                      String acquisitionCondition, String iconUrl) {
        Title title = Title.builder()
            .name(name)
            .description(description)
            .rarity(rarity)
            .positionType(positionType)
            .acquisitionType(acquisitionType)
            .acquisitionCondition(acquisitionCondition)
            .colorCode(rarity.getColorCode())
            .iconUrl(iconUrl)
            .build();

        Title saved = titleRepository.save(title);
        log.info("칭호 생성: name={}, rarity={}, position={}", name, rarity, positionType);

        return TitleResponse.from(saved);
    }

    /**
     * 신규 사용자에게 기본 칭호 부여 및 장착
     * LEFT: 신입 (id: 1), RIGHT: 모험가 (id: 26)
     */
    @Transactional
    public void grantAndEquipDefaultTitles(String userId) {
        // 기본 칭호 ID (DML에서 정의된 값)
        final Long DEFAULT_LEFT_TITLE_ID = 1L;   // 신입
        final Long DEFAULT_RIGHT_TITLE_ID = 26L; // 모험가

        // LEFT 칭호 "신입" 부여 및 장착 (기본 칭호는 알림 제외)
        grantTitle(userId, DEFAULT_LEFT_TITLE_ID, false);
        equipTitle(userId, DEFAULT_LEFT_TITLE_ID);

        // RIGHT 칭호 "모험가" 부여 및 장착 (기본 칭호는 알림 제외)
        grantTitle(userId, DEFAULT_RIGHT_TITLE_ID, false);
        equipTitle(userId, DEFAULT_RIGHT_TITLE_ID);

        log.info("기본 칭호 부여 완료: userId={}, left=신입, right=모험가", userId);
    }

    // 기본 칭호 초기화
    @Transactional
    public void initializeDefaultTitles() {
        if (titleRepository.count() > 0) {
            return;
        }

        // LEFT 칭호 (형용사/부사형)
        createTitle("신입", "이제 막 시작한", TitleRarity.COMMON, TitlePosition.LEFT,
            TitleAcquisitionType.LEVEL, "레벨 1 달성", null);
        createTitle("성실한", "꾸준히 노력하는", TitleRarity.COMMON, TitlePosition.LEFT,
            TitleAcquisitionType.ACHIEVEMENT, "7일 연속 출석", null);
        createTitle("노력하는", "끊임없이 노력하는", TitleRarity.UNCOMMON, TitlePosition.LEFT,
            TitleAcquisitionType.LEVEL, "레벨 10 달성", null);
        createTitle("숙련된", "기술을 연마한", TitleRarity.RARE, TitlePosition.LEFT,
            TitleAcquisitionType.LEVEL, "레벨 30 달성", null);
        createTitle("전설적인", "전설로 기록될", TitleRarity.EPIC, TitlePosition.LEFT,
            TitleAcquisitionType.LEVEL, "레벨 50 달성", null);
        createTitle("궁극의", "최고 경지에 도달한", TitleRarity.LEGENDARY, TitlePosition.LEFT,
            TitleAcquisitionType.LEVEL, "레벨 100 달성", null);

        // RIGHT 칭호 (명사형)
        createTitle("모험가", "모험을 시작한 자", TitleRarity.COMMON, TitlePosition.RIGHT,
            TitleAcquisitionType.LEVEL, "레벨 1 달성", null);
        createTitle("전사", "강인한 의지의 전사", TitleRarity.UNCOMMON, TitlePosition.RIGHT,
            TitleAcquisitionType.LEVEL, "레벨 15 달성", null);
        createTitle("영웅", "영웅의 자격을 가진 자", TitleRarity.RARE, TitlePosition.RIGHT,
            TitleAcquisitionType.LEVEL, "레벨 40 달성", null);
        createTitle("현자", "지혜로운 자", TitleRarity.EPIC, TitlePosition.RIGHT,
            TitleAcquisitionType.LEVEL, "레벨 70 달성", null);
        createTitle("레전드", "전설이 된 자", TitleRarity.LEGENDARY, TitlePosition.RIGHT,
            TitleAcquisitionType.ACHIEVEMENT, "미션 500회 완료", null);

        log.info("기본 칭호 초기화 완료");
    }
}
