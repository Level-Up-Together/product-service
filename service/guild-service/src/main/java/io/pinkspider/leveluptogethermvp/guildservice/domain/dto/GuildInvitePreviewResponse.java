package io.pinkspider.leveluptogethermvp.guildservice.domain.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

/**
 * 길드 초대 링크 미리보기 응답 DTO (LUT-519, 비로그인 허용).
 *
 * <p>잘못된/삭제된 코드도 예외가 아니라 데이터로 표현한다({@code valid=false}) — 비로그인 GET 이라 500 대신
 * 랜딩 화면이 사유를 안내할 수 있게 한다.
 *
 * @param valid 코드가 활성 길드로 해석됨
 * @param joinable 이 뷰어가 합류 가능(정원 여유). 이미 멤버면 true(길드로 바로 이동 가능)
 * @param alreadyMember 뷰어가 이미 길드원 (비로그인이면 false)
 * @param reason 합류 불가 사유: {@code null} | {@code "FULL"} | {@code "INVALID"}
 * @param guild 길드 카드 정보 ({@code valid=false} 면 null)
 */
@Builder
public record GuildInvitePreviewResponse(
    @JsonProperty("valid") boolean valid,
    @JsonProperty("joinable") boolean joinable,
    @JsonProperty("already_member") boolean alreadyMember,
    @JsonInclude(JsonInclude.Include.NON_NULL) @JsonProperty("reason") String reason,
    @JsonInclude(JsonInclude.Include.NON_NULL) @JsonProperty("guild") GuildResponse guild) {

    /** 코드가 없거나 비활성 길드를 가리킬 때. */
    public static GuildInvitePreviewResponse invalid() {
        return GuildInvitePreviewResponse.builder()
            .valid(false)
            .joinable(false)
            .alreadyMember(false)
            .reason("INVALID")
            .build();
    }
}
