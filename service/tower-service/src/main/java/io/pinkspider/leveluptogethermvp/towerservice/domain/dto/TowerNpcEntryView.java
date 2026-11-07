package io.pinkspider.leveluptogethermvp.towerservice.domain.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.pinkspider.leveluptogethermvp.towerservice.application.visitor.TowerVisitorTag;
import io.pinkspider.leveluptogethermvp.towerservice.domain.entity.TowerNpcEntry;
import java.time.LocalDateTime;

/**
 * LUT-760: 공개 글 하나 (제출 응답·공개 조회). 이메일은 절대 들어가지 않는다. 작성자 표시: 로그인 유저 = {@code author_name}(제출 때 닉네임)
 * · 방문객 = {@code visitor_no}(클라이언트가 locale 로 "방문객 4821" 조립) · 시드 = {@code is_seed=true}(클라이언트가 NPC
 * 이름을 쓴다). {@code user_id}·{@code visitor_tag} 는 차단 유저·숨긴 방문객 글을 클라이언트가 빼는 데 쓴다(채팅 {@code
 * visibleChat} 과 같은 규칙).
 */
@JsonNaming(SnakeCaseStrategy.class)
public record TowerNpcEntryView(
        Long entryId,
        String body,
        String authorName,
        Integer visitorNo,
        String visitorTag,
        String userId,
        Boolean isSeed,
        LocalDateTime createdAt) {

    public static TowerNpcEntryView from(TowerNpcEntry e) {
        return new TowerNpcEntryView(
                e.getId(),
                e.getBody(),
                e.getAuthorName(),
                e.getVisitorNo(),
                TowerVisitorTag.of(e.getVisitorKey()),
                e.getUserId(),
                e.isSeedEntry(),
                e.getCreatedAt());
    }
}
