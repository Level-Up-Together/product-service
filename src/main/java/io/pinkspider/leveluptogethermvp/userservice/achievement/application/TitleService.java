package io.pinkspider.leveluptogethermvp.userservice.achievement.application;

import io.pinkspider.leveluptogethermvp.userservice.achievement.domain.dto.TitleResponse;
import io.pinkspider.leveluptogethermvp.userservice.achievement.domain.dto.UserTitleResponse;
import io.pinkspider.leveluptogethermvp.userservice.achievement.domain.entity.Title;
import io.pinkspider.leveluptogethermvp.userservice.achievement.domain.entity.UserTitle;
import io.pinkspider.leveluptogethermvp.userservice.achievement.domain.enums.TitleRarity;
import io.pinkspider.leveluptogethermvp.userservice.achievement.infrastructure.TitleRepository;
import io.pinkspider.leveluptogethermvp.userservice.achievement.infrastructure.UserTitleRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TitleService {

    private final TitleRepository titleRepository;
    private final UserTitleRepository userTitleRepository;

    // 전체 칭호 목록
    public List<TitleResponse> getAllTitles() {
        return titleRepository.findByIsActiveTrue().stream()
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

    // 장착된 칭호 조회
    public Optional<UserTitleResponse> getEquippedTitle(String userId) {
        return userTitleRepository.findEquippedByUserId(userId)
            .map(UserTitleResponse::from);
    }

    // 칭호 부여
    @Transactional
    public UserTitleResponse grantTitle(String userId, Long titleId) {
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
        log.info("칭호 획득: userId={}, title={}", userId, title.getName());

        return UserTitleResponse.from(saved);
    }

    // 칭호 장착
    @Transactional
    public UserTitleResponse equipTitle(String userId, Long titleId) {
        UserTitle userTitle = userTitleRepository.findByUserIdAndTitleId(userId, titleId)
            .orElseThrow(() -> new IllegalArgumentException("보유하지 않은 칭호입니다."));

        // 기존 장착 해제
        userTitleRepository.unequipAllByUserId(userId);

        // 새 칭호 장착
        userTitle.equip();
        log.info("칭호 장착: userId={}, title={}", userId, userTitle.getTitle().getName());

        return UserTitleResponse.from(userTitle);
    }

    // 칭호 해제
    @Transactional
    public void unequipTitle(String userId) {
        userTitleRepository.unequipAllByUserId(userId);
        log.info("칭호 해제: userId={}", userId);
    }

    // 칭호 생성 (관리자용)
    @Transactional
    public TitleResponse createTitle(String name, String description, TitleRarity rarity,
                                      String prefix, String suffix, String iconUrl) {
        Title title = Title.builder()
            .name(name)
            .description(description)
            .rarity(rarity)
            .prefix(prefix)
            .suffix(suffix)
            .colorCode(rarity.getColorCode())
            .iconUrl(iconUrl)
            .build();

        Title saved = titleRepository.save(title);
        log.info("칭호 생성: name={}, rarity={}", name, rarity);

        return TitleResponse.from(saved);
    }

    // 기본 칭호 초기화
    @Transactional
    public void initializeDefaultTitles() {
        if (titleRepository.count() > 0) {
            return;
        }

        createTitle("새내기", "처음 시작하는 모험가", TitleRarity.COMMON, null, null, null);
        createTitle("성실한 자", "꾸준히 미션을 수행하는 자", TitleRarity.COMMON, null, null, null);
        createTitle("미션 마니아", "미션을 사랑하는 자", TitleRarity.UNCOMMON, null, null, null);
        createTitle("길드의 일원", "길드에 가입한 자", TitleRarity.COMMON, null, null, null);
        createTitle("길드 마스터", "길드를 이끄는 자", TitleRarity.RARE, null, null, null);
        createTitle("끈기의 달인", "7일 연속 미션 수행", TitleRarity.UNCOMMON, null, null, null);
        createTitle("철인", "30일 연속 미션 수행", TitleRarity.RARE, null, null, null);
        createTitle("전설의 시작", "100회 미션 완료", TitleRarity.EPIC, "[전설의]", null, null);
        createTitle("불굴의 의지", "100일 연속 미션 수행", TitleRarity.LEGENDARY, null, "의 수호자", null);

        log.info("기본 칭호 초기화 완료");
    }
}
