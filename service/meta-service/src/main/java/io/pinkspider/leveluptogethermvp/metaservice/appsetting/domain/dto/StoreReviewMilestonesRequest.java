package io.pinkspider.leveluptogethermvp.metaservice.appsetting.domain.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/** LUT-708: 스토어 인앱 평가 팝업 마일스톤(연속 출석 일수) 변경 요청. 빈 목록 = 끔 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record StoreReviewMilestonesRequest(@NotNull List<Integer> milestoneDays) {}
