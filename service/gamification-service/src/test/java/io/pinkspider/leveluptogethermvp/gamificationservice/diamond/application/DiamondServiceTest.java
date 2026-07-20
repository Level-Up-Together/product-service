package io.pinkspider.leveluptogethermvp.gamificationservice.diamond.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.pinkspider.leveluptogethermvp.gamificationservice.diamond.domain.dto.UserDiamondHistoryAdminPageResponse;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.entity.DiamondHistory;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.entity.UserDiamond;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.enums.DiamondType;
import io.pinkspider.leveluptogethermvp.gamificationservice.infrastructure.DiamondHistoryRepository;
import io.pinkspider.leveluptogethermvp.gamificationservice.infrastructure.UserDiamondRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class DiamondServiceTest {

    @Mock
    private UserDiamondRepository userDiamondRepository;

    @Mock
    private DiamondHistoryRepository diamondHistoryRepository;

    @InjectMocks
    private DiamondService diamondService;

    private static final String USER_ID = "user-1";

    private UserDiamond diamond(int balance, int lastRewardedLevel) {
        return UserDiamond.builder()
            .userId(USER_ID)
            .balance(balance)
            .lastRewardedLevel(lastRewardedLevel)
            .build();
    }

    @Nested
    @DisplayName("awardLevelUpDiamonds 테스트")
    class AwardLevelUpTest {

        @Test
        @DisplayName("레벨 1→2 레벨업 시 1개 지급하고 lastRewardedLevel 을 갱신한다")
        void awardsSingleLevelUp() {
            UserDiamond diamond = diamond(0, 1);
            when(userDiamondRepository.findByUserId(USER_ID)).thenReturn(Optional.of(diamond));

            int granted = diamondService.awardLevelUpDiamonds(USER_ID, 2);

            assertThat(granted).isEqualTo(1);
            assertThat(diamond.getBalance()).isEqualTo(1);
            assertThat(diamond.getLastRewardedLevel()).isEqualTo(2);

            ArgumentCaptor<DiamondHistory> captor = ArgumentCaptor.forClass(DiamondHistory.class);
            verify(diamondHistoryRepository).save(captor.capture());
            DiamondHistory history = captor.getValue();
            assertThat(history.getType()).isEqualTo(DiamondType.LEVEL_UP);
            assertThat(history.getAmount()).isEqualTo(1);
            assertThat(history.getBalanceAfter()).isEqualTo(1);
            assertThat(history.getDescription()).isEqualTo("Lv.2 레벨업 보상");
        }

        @Test
        @DisplayName("여러 레벨 점프 시 레벨당 1개씩 지급한다")
        void awardsMultipleLevels() {
            UserDiamond diamond = diamond(5, 3);
            when(userDiamondRepository.findByUserId(USER_ID)).thenReturn(Optional.of(diamond));

            int granted = diamondService.awardLevelUpDiamonds(USER_ID, 6);

            assertThat(granted).isEqualTo(3); // Lv.4, 5, 6
            assertThat(diamond.getBalance()).isEqualTo(8);
            assertThat(diamond.getLastRewardedLevel()).isEqualTo(6);
            verify(diamondHistoryRepository, times(3)).save(any(DiamondHistory.class));
        }

        @Test
        @DisplayName("이미 보상 지급된 레벨 이하로는 지급하지 않는다 (레벨다운 후 재상승 중복 방지)")
        void skipsAlreadyRewardedLevels() {
            UserDiamond diamond = diamond(10, 10);
            when(userDiamondRepository.findByUserId(USER_ID)).thenReturn(Optional.of(diamond));

            int granted = diamondService.awardLevelUpDiamonds(USER_ID, 8);

            assertThat(granted).isZero();
            verify(diamondHistoryRepository, never()).save(any());
        }

        @Test
        @DisplayName("레벨업 보상은 Lv.1000(총 999개)까지만 지급된다")
        void capsAtMaxRewardedLevel() {
            UserDiamond diamond = diamond(998, 999);
            when(userDiamondRepository.findByUserId(USER_ID)).thenReturn(Optional.of(diamond));

            int granted = diamondService.awardLevelUpDiamonds(USER_ID, 1500);

            assertThat(granted).isEqualTo(1); // Lv.1000 몫만
            assertThat(diamond.getLastRewardedLevel()).isEqualTo(1000);
            assertThat(diamond.getBalance()).isEqualTo(999);
        }

        @Test
        @DisplayName("user_diamond 행이 없으면 생성 후 지급한다")
        void createsUserDiamondIfAbsent() {
            when(userDiamondRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());
            when(userDiamondRepository.save(any(UserDiamond.class)))
                .thenAnswer(inv -> inv.getArgument(0));

            int granted = diamondService.awardLevelUpDiamonds(USER_ID, 2);

            assertThat(granted).isEqualTo(1);
            verify(userDiamondRepository).save(any(UserDiamond.class));
        }
    }

    @Nested
    @DisplayName("awardLevelUpDiamondsAggregated 테스트 (마이그레이션)")
    class AwardAggregatedTest {

        @Test
        @DisplayName("여러 레벨 몫을 한 행으로 일괄 지급한다")
        void aggregatesIntoSingleRow() {
            UserDiamond diamond = diamond(0, 1);
            when(userDiamondRepository.findByUserId(USER_ID)).thenReturn(Optional.of(diamond));

            int granted = diamondService.awardLevelUpDiamondsAggregated(USER_ID, 37);

            assertThat(granted).isEqualTo(36); // Lv.2~Lv.37
            assertThat(diamond.getBalance()).isEqualTo(36);
            assertThat(diamond.getLastRewardedLevel()).isEqualTo(37);

            ArgumentCaptor<DiamondHistory> captor = ArgumentCaptor.forClass(DiamondHistory.class);
            verify(diamondHistoryRepository).save(captor.capture());
            assertThat(captor.getValue().getAmount()).isEqualTo(36);
            assertThat(captor.getValue().getDescription()).isEqualTo("Lv.2~Lv.37 레벨업 보상");
        }

        @Test
        @DisplayName("레벨 1 유저는 지급 대상이 아니다")
        void skipsLevelOneUser() {
            UserDiamond diamond = diamond(0, 1);
            when(userDiamondRepository.findByUserId(USER_ID)).thenReturn(Optional.of(diamond));

            int granted = diamondService.awardLevelUpDiamondsAggregated(USER_ID, 1);

            assertThat(granted).isZero();
            verify(diamondHistoryRepository, never()).save(any());
        }

        @Test
        @DisplayName("재실행 시 이미 지급된 몫은 지급하지 않는다 (멱등)")
        void idempotentOnRerun() {
            UserDiamond diamond = diamond(36, 37);
            when(userDiamondRepository.findByUserId(USER_ID)).thenReturn(Optional.of(diamond));

            int granted = diamondService.awardLevelUpDiamondsAggregated(USER_ID, 37);

            assertThat(granted).isZero();
            verify(diamondHistoryRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("awardMissionBookDiamond 테스트")
    class AwardMissionBookTest {

        @Test
        @DisplayName("최초 목표달성 시 1개 지급하고 '{미션명} 목표달성' 설명을 기록한다")
        void awardsFirstAchievement() {
            when(diamondHistoryRepository.existsByUserIdAndTypeAndSourceId(
                USER_ID, DiamondType.MISSION_BOOK, 77L)).thenReturn(false);
            UserDiamond diamond = diamond(3, 5);
            when(userDiamondRepository.findByUserId(USER_ID)).thenReturn(Optional.of(diamond));

            boolean awarded = diamondService.awardMissionBookDiamond(USER_ID, 77L, "아침 스트레칭");

            assertThat(awarded).isTrue();
            assertThat(diamond.getBalance()).isEqualTo(4);

            ArgumentCaptor<DiamondHistory> captor = ArgumentCaptor.forClass(DiamondHistory.class);
            verify(diamondHistoryRepository).save(captor.capture());
            DiamondHistory history = captor.getValue();
            assertThat(history.getType()).isEqualTo(DiamondType.MISSION_BOOK);
            assertThat(history.getSourceId()).isEqualTo(77L);
            assertThat(history.getDescription()).isEqualTo("아침 스트레칭 목표달성");
        }

        @Test
        @DisplayName("이미 지급된 템플릿에는 재지급하지 않는다")
        void skipsAlreadyAwardedTemplate() {
            when(diamondHistoryRepository.existsByUserIdAndTypeAndSourceId(
                USER_ID, DiamondType.MISSION_BOOK, 77L)).thenReturn(true);

            boolean awarded = diamondService.awardMissionBookDiamond(USER_ID, 77L, "아침 스트레칭");

            assertThat(awarded).isFalse();
            verify(diamondHistoryRepository, never()).save(any());
        }

        @Test
        @DisplayName("templateId 가 null 이면 지급하지 않는다")
        void skipsNullTemplateId() {
            boolean awarded = diamondService.awardMissionBookDiamond(USER_ID, null, "미션");

            assertThat(awarded).isFalse();
            verify(diamondHistoryRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("spendDiamonds 테스트")
    class SpendTest {

        @Test
        @DisplayName("차감 시 음수 amount 와 '{아이템 이름} 구매' 설명으로 기록한다")
        void spendsWithNegativeAmount() {
            UserDiamond diamond = diamond(10, 5);
            when(userDiamondRepository.findByUserId(USER_ID)).thenReturn(Optional.of(diamond));

            int balanceAfter = diamondService.spendDiamonds(USER_ID, 3, 9L, "프로필 테두리");

            assertThat(balanceAfter).isEqualTo(7);

            ArgumentCaptor<DiamondHistory> captor = ArgumentCaptor.forClass(DiamondHistory.class);
            verify(diamondHistoryRepository).save(captor.capture());
            DiamondHistory history = captor.getValue();
            assertThat(history.getType()).isEqualTo(DiamondType.SHOP);
            assertThat(history.getAmount()).isEqualTo(-3);
            assertThat(history.getBalanceAfter()).isEqualTo(7);
            assertThat(history.getDescription()).isEqualTo("프로필 테두리 구매");
        }

        @Test
        @DisplayName("잔액 부족 시 예외가 발생한다")
        void failsOnInsufficientBalance() {
            UserDiamond diamond = diamond(2, 5);
            when(userDiamondRepository.findByUserId(USER_ID)).thenReturn(Optional.of(diamond));

            assertThatThrownBy(() -> diamondService.spendDiamonds(USER_ID, 3, 9L, "프로필 테두리"))
                .isInstanceOf(IllegalStateException.class);
        }
    }

    @Nested
    @DisplayName("getUserDiamondHistory 테스트")
    class GetHistoryTest {

        @Test
        @DisplayName("이력과 현재 잔액을 함께 반환한다")
        void returnsHistoryWithBalance() {
            DiamondHistory history = DiamondHistory.builder()
                .userId(USER_ID)
                .type(DiamondType.LEVEL_UP)
                .sourceId(2L)
                .amount(1)
                .balanceAfter(1)
                .description("Lv.2 레벨업 보상")
                .build();
            when(diamondHistoryRepository.findByUserIdOrderByIdDesc(USER_ID, PageRequest.of(0, 10)))
                .thenReturn(new PageImpl<>(List.of(history)));
            when(userDiamondRepository.findByUserId(USER_ID))
                .thenReturn(Optional.of(diamond(1, 2)));

            UserDiamondHistoryAdminPageResponse result =
                diamondService.getUserDiamondHistory(USER_ID, PageRequest.of(0, 10));

            assertThat(result.content()).hasSize(1);
            assertThat(result.content().get(0).type()).isEqualTo("LEVEL_UP");
            assertThat(result.content().get(0).amount()).isEqualTo(1);
            assertThat(result.content().get(0).balanceAfter()).isEqualTo(1);
            assertThat(result.currentBalance()).isEqualTo(1);
        }

        @Test
        @DisplayName("이력이 없으면 빈 목록과 잔액 0을 반환한다")
        void returnsEmptyForNoHistory() {
            Pageable pageable = PageRequest.of(0, 10);
            when(diamondHistoryRepository.findByUserIdOrderByIdDesc(USER_ID, pageable))
                .thenReturn(new PageImpl<>(List.of()));
            when(userDiamondRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());

            UserDiamondHistoryAdminPageResponse result =
                diamondService.getUserDiamondHistory(USER_ID, pageable);

            assertThat(result.content()).isEmpty();
            assertThat(result.currentBalance()).isZero();
        }
    }

    @Nested
    @DisplayName("getBalance 테스트 (LUT-248)")
    class GetBalanceTest {

        @Test
        @DisplayName("현재 보유 다이아 잔액을 반환한다")
        void returnsBalance() {
            when(userDiamondRepository.findByUserId(USER_ID))
                .thenReturn(Optional.of(diamond(42, 42)));

            assertThat(diamondService.getBalance(USER_ID)).isEqualTo(42);
        }

        @Test
        @DisplayName("지급 이력이 없으면 0을 반환한다")
        void returnsZeroWhenAbsent() {
            when(userDiamondRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());

            assertThat(diamondService.getBalance(USER_ID)).isZero();
        }
    }
}
