package io.pinkspider.leveluptogethermvp.towerservice.domain.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TowerInstanceTest {

    private static TowerInstance room(long id, Integer no) {
        return TowerInstance.builder().id(id).floorNumber(1).instanceNo(no).build();
    }

    @Test
    @DisplayName("LUT-738 화면 번호: 저장된 번호는 그대로, 없는 방은 쓰이지 않은 가장 작은 번호부터 id 순 · 다음 번호")
    void numbers() {
        Map<Long, Integer> numbers =
                TowerInstance.displayNumbers(
                        List.of(
                                room(1, 2),
                                room(2, null),
                                room(3, 4),
                                room(4, null),
                                room(5, null)));
        assertThat(numbers)
                .containsExactly(
                        Map.entry(1L, 2),
                        Map.entry(2L, 1),
                        Map.entry(3L, 4),
                        Map.entry(4L, 3),
                        Map.entry(5L, 5));
        assertThat(TowerInstance.displayNumbers(List.of())).isEmpty();
        assertThat(TowerInstance.nextNumber(Set.of())).isEqualTo(1);
        assertThat(TowerInstance.nextNumber(Set.of(1, 2, 4))).isEqualTo(3);

        TowerInstance opened = TowerInstance.open(2, 3, java.time.LocalDateTime.now());
        assertThat(opened.getInstanceNo()).isEqualTo(3);
        assertThat(opened.getFloorNumber()).isEqualTo(2);
        TowerInstance legacy = room(9, null);
        legacy.assignNumber(7);
        assertThat(legacy.getInstanceNo()).isEqualTo(7);
    }
}
