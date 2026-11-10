package io.pinkspider.leveluptogethermvp.towerservice.presence;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerInstanceEvent;
import java.util.List;

/**
 * LUT-631: 다른 유저에게 내려가는 한 사람의 모습 — 입장 스냅샷·ENTER 이벤트 공용. 방문객은 {@code user_id}·{@code level} 없이
 * {@code visitor_no} (클라이언트가 locale 에 맞춰 "방문객 1234" 로 조립)와 {@code visitor_tag}(LUT-705, 세션 기준
 * 숨기기용). 연결 여부·자동 착석 여부는 내리지 않는다. LUT-749: {@code mission} = 미션 중이고 공개 미션일 때만 {@code {title,
 * category_id}}, 아니면 내리지 않는다(USER_STATE 와 같은 규칙) — 들어올 때 이미 미션 중이던 사람의 미션명.
 */
@JsonNaming(SnakeCaseStrategy.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TowerPresenceView(
        Long occupantId,
        String userId,
        String kind,
        Integer visitorNo,
        String nickname,
        String picture,
        Integer level,
        List<TowerAvatarItem> items,
        Integer x,
        Integer y,
        String state,
        String visitorTag,
        TowerInstanceEvent.MissionInfo mission) {}
