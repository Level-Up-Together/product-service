package io.pinkspider.leveluptogethermvp.towerservice.application;

import static io.pinkspider.leveluptogethermvp.towerservice.TowerTestFixtures.floor;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.pinkspider.leveluptogethermvp.towerservice.application.exception.TowerFloorException;
import io.pinkspider.leveluptogethermvp.towerservice.domain.entity.TowerFloor;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

/** LUT-629 저장 규칙 7개 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TowerFloorRuleValidatorTest {

    @Mock private FloorMaskChecker floorMaskChecker;

    @InjectMocks private TowerFloorRuleValidator validator;

    @BeforeEach
    void setUp() throws IOException {
        ReflectionTestUtils.setField(validator, "maskCheckEnabled", true);
        when(floorMaskChecker.isWalkable(anyString(), anyString(), anyInt(), anyInt()))
                .thenReturn(true);
    }

    private static void assertCode(Runnable r, String code) {
        assertThatThrownBy(r::run)
                .isInstanceOf(TowerFloorException.class)
                .extracting("code")
                .isEqualTo(code);
    }

    @Test
    @DisplayName("1층 제한 없음·2층 5·3층 5(같은 값 허용), 1·2층 비로그인, 전부 공개 — 통과")
    void validSet() {
        TowerFloor f1 = floor(1L, 1, null, true, true);
        // 입장 레벨이 있는 2·3층은 비로그인 허용 불가, 같은 입장 레벨(5)은 허용
        TowerFloor f2 = floor(2L, 2, 5, false, true);
        TowerFloor f3 = floor(3L, 3, 5, false, true);
        assertThatCode(() -> validator.validate(List.of(f1, f2, f3), f3))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("1·2·3층 모두 제한 없음도 저장할 수 있다")
    void allUnlimited() {
        TowerFloor f1 = floor(1L, 1, null, true, true);
        TowerFloor f2 = floor(2L, 2, null, true, true);
        TowerFloor f3 = floor(null, 3, null, false, false);
        assertThatCode(() -> validator.validate(List.of(f1, f2, f3), f3))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("층 번호 중복 → 150102")
    void duplicateNumber() {
        TowerFloor f1 = floor(1L, 1, null, true, true);
        TowerFloor dup = floor(null, 1, null, true, true);
        assertCode(
                () -> validator.validate(List.of(f1, dup), dup),
                TowerFloorException.DUPLICATE_NUMBER);
    }

    @Test
    @DisplayName("1층에 입장 레벨 지정 → 150104")
    void floor1EntryLevel() {
        TowerFloor f1 = floor(null, 1, 3, false, true);
        assertCode(
                () -> validator.validate(List.of(f1), f1), TowerFloorException.FLOOR1_ENTRY_LEVEL);
    }

    @Test
    @DisplayName("입장 레벨이 있는 층에 비로그인 허용 → 150105")
    void guestRequiresNoLevel() {
        TowerFloor f1 = floor(1L, 1, null, true, true);
        TowerFloor f2 = floor(null, 2, 5, true, true);
        assertCode(
                () -> validator.validate(List.of(f1, f2), f2),
                TowerFloorException.GUEST_REQUIRES_NO_LEVEL);
    }

    @Test
    @DisplayName("위층 입장 레벨이 아래층보다 낮음 → 150103 (제한 없음은 가장 낮은 값이라 제한 있는 층 위에 둘 수 없다)")
    void entryLevelOrder() {
        TowerFloor f1 = floor(1L, 1, null, true, true);
        TowerFloor f2 = floor(2L, 2, 10, false, true);
        TowerFloor f3 = floor(null, 3, 5, false, true);
        assertCode(
                () -> validator.validate(List.of(f1, f2, f3), f3),
                TowerFloorException.ENTRY_LEVEL_ORDER);

        TowerFloor f3Unlimited = floor(null, 3, null, false, true);
        assertCode(
                () -> validator.validate(List.of(f1, f2, f3Unlimited), f3Unlimited),
                TowerFloorException.ENTRY_LEVEL_ORDER);
    }

    @Test
    @DisplayName("아래층 비허용·위층 허용 → 150106 (비로그인 허용은 1층부터 이어져야 한다)")
    void guestNotContiguous() {
        TowerFloor f1 = floor(1L, 1, null, false, true);
        TowerFloor f2 = floor(null, 2, null, true, true);
        assertCode(
                () -> validator.validate(List.of(f1, f2), f2),
                TowerFloorException.GUEST_NOT_CONTIGUOUS);
    }

    @Test
    @DisplayName("공개 층 번호가 1부터 연속이 아님 → 150107 (아래층 비공개·위층 공개, 빈 번호)")
    void publicNotContiguous() {
        TowerFloor hidden1 = floor(1L, 1, null, true, false);
        TowerFloor public2 = floor(null, 2, null, true, true);
        assertCode(
                () -> validator.validate(List.of(hidden1, public2), public2),
                TowerFloorException.PUBLIC_NOT_CONTIGUOUS);

        TowerFloor public1 = floor(1L, 1, null, true, true);
        TowerFloor public3 = floor(null, 3, null, false, true);
        assertCode(
                () -> validator.validate(List.of(public1, public3), public3),
                TowerFloorException.PUBLIC_NOT_CONTIGUOUS);
    }

    @Test
    @DisplayName("입장 지점이 마스크 밖 → 150108, 마스크를 못 읽으면 → 150109")
    void entryPointMask() throws IOException {
        TowerFloor f1 = floor(null, 1, null, true, true);
        when(floorMaskChecker.isWalkable(anyString(), anyString(), anyInt(), anyInt()))
                .thenReturn(false);
        assertCode(
                () -> validator.validate(List.of(f1), f1),
                TowerFloorException.ENTRY_POINT_OUTSIDE_MASK);

        when(floorMaskChecker.isWalkable(anyString(), anyString(), anyInt(), anyInt()))
                .thenThrow(new IOException("404"));
        assertCode(() -> validator.validate(List.of(f1), f1), TowerFloorException.MASK_UNREADABLE);
    }

    @Test
    @DisplayName("마스크 검사를 끄면(dev) 입장 지점은 보지 않는다 · 삭제 검증(target null)도 마스크를 읽지 않는다")
    void maskCheckSkipped() throws IOException {
        ReflectionTestUtils.setField(validator, "maskCheckEnabled", false);
        TowerFloor f1 = floor(null, 1, null, true, true);
        assertThatCode(() -> validator.validate(List.of(f1), f1)).doesNotThrowAnyException();
        verify(floorMaskChecker, never()).isWalkable(anyString(), anyString(), anyInt(), anyInt());

        ReflectionTestUtils.setField(validator, "maskCheckEnabled", true);
        TowerFloor f2 = floor(2L, 2, 5, false, true);
        assertThatCode(() -> validator.validate(List.of(floor(1L, 1, null, true, true), f2), null))
                .doesNotThrowAnyException();
        verify(floorMaskChecker, never()).isWalkable(anyString(), isNull(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("삭제 검증: 1층을 지워 공개 2층만 남으면 → 150107")
    void deleteBreaksPublicContiguity() {
        TowerFloor f2 = floor(2L, 2, null, false, true);
        assertCode(
                () -> validator.validate(List.of(f2), null),
                TowerFloorException.PUBLIC_NOT_CONTIGUOUS);
    }
}
