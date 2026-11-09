package io.pinkspider.leveluptogethermvp.towerservice.domain.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.List;

/**
 * LUT-693: 실제 흔적 — 오늘(KST) 이 층에서 미션을 완료한 사람 수(실제 수치만, NPC 없음)와 완료한 자리 좌표(누구인지는 내리지 않는다). 사람이 0명일 때
 * NPC 와 함께 밀도를 보여주는 데 쓴다.
 */
@JsonNaming(SnakeCaseStrategy.class)
public record TowerTraceView(long completedToday, List<Point> seats) {

    public static final TowerTraceView EMPTY = new TowerTraceView(0, List.of());

    @JsonNaming(SnakeCaseStrategy.class)
    public record Point(int x, int y) {}
}
