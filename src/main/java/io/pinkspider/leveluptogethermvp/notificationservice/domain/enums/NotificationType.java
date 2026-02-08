package io.pinkspider.leveluptogethermvp.notificationservice.domain.enums;

import java.text.MessageFormat;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum NotificationType {
    // 시스템
    SYSTEM("시스템", "SYSTEM", null, null, null, null, false),
    ANNOUNCEMENT("공지사항", "SYSTEM", null, null, null, null, false),
    WELCOME("가입 환영", "SYSTEM",
        "Level Up Together에 오신 것을 환영합니다!",
        "{0}님, 함께 성장하는 여정을 시작해보세요.",
        null, "/home", false),
    LEVEL_UP("레벨 업", "SYSTEM", null, null, null, null, false),
    GUILD_CREATION_ELIGIBLE("길드 창설 가능", "SYSTEM",
        "길드 창설이 가능해졌습니다!",
        "레벨 20에 도달하여 이제 나만의 길드를 만들 수 있습니다.",
        "LEVEL", "/guilds/create", false),

    // 친구 관련
    FRIEND_REQUEST("친구 요청", "FRIEND",
        "새 친구 요청",
        "{0}님이 친구 요청을 보냈습니다.",
        "FRIEND_REQUEST", "/mypage/friends/requests", false),
    FRIEND_ACCEPTED("친구 수락", "FRIEND",
        "친구 요청 수락",
        "{0}님이 친구 요청을 수락했습니다.",
        "FRIEND", "/mypage/friends", false),
    FRIEND_REJECTED("친구 거절", "FRIEND",
        "친구 요청 거절",
        "{0}님이 친구 요청을 거절했습니다.",
        "FRIEND_REQUEST", "/mypage/friends", false),

    // 길드 관련
    GUILD_INVITE("길드 초대", "GUILD",
        "길드 초대",
        "{0}님이 ''{1}'' 길드로 초대했습니다.",
        "GUILD_INVITATION", "/guild-invitations/{id}", false),
    GUILD_JOIN_REQUEST("길드 가입 신청", "GUILD",
        "길드 가입 신청",
        "{0}님이 길드 가입을 신청했습니다.",
        "GUILD", "/guild/{id}/members", false),
    GUILD_JOIN_APPROVED("길드 가입 승인", "GUILD",
        "길드 가입 승인",
        "''{0}'' 길드에 가입되었습니다!",
        "GUILD", "/guild/{id}", false),
    GUILD_JOIN_REJECTED("길드 가입 거절", "GUILD",
        "길드 가입 거절",
        "''{0}'' 길드 가입이 거절되었습니다.",
        "GUILD", "/guild", false),
    GUILD_MISSION_ARRIVED("길드 미션 도착", "GUILD",
        "새 길드 미션",
        "''{0}'' 길드 미션이 도착했습니다.",
        "MISSION", "/mission", false),
    GUILD_BULLETIN("길드 공지사항", "GUILD",
        "새 길드 공지사항",
        "[{0}] {1}",
        "GUILD_POST", "/guild/{2}/posts/{id}", false),
    GUILD_CHAT("길드 채팅", "GUILD",
        "{0}",
        "{1}: {2}",
        "GUILD_CHAT", "/guild/{3}/chat", false),

    // 소셜 관련 (댓글)
    COMMENT_ON_MY_FEED("내 글에 댓글", "SOCIAL",
        "새 댓글",
        "{0}님이 회원님의 글에 댓글을 남겼습니다.",
        "FEED", "/home/{id}", false),
    COMMENT_ON_MY_MISSION("내 미션에 댓글", "SOCIAL",
        "새 댓글",
        "{0}님이 ''{1}'' 미션에 댓글을 남겼습니다.",
        "MISSION", "/mission/progress/{id}", false),

    // 미션 관련
    MISSION("미션", "MISSION", null, null, null, null, false),
    MISSION_COMPLETED("미션 완료", "MISSION", null, null, null, null, false),

    // 업적/칭호 관련
    ACHIEVEMENT("업적", "ACHIEVEMENT", null, null, null, null, false),
    ACHIEVEMENT_COMPLETED("업적 달성", "ACHIEVEMENT",
        "업적 달성!",
        "''{0}'' 업적을 달성했습니다!",
        "ACHIEVEMENT", "/mypage", true),
    TITLE_ACQUIRED("칭호 획득", "ACHIEVEMENT",
        "새로운 칭호 획득!",
        "''{0}'' 칭호를 획득했습니다!",
        "TITLE", "/mypage/titles", true),

    // 신고 관련
    CONTENT_REPORTED("콘텐츠 신고", "SYSTEM", null, null, null, null, false);

    private final String displayName;
    private final String category;
    private final String defaultTitle;
    private final String messageTemplate;
    private final String referenceType;
    private final String actionUrlPattern;
    private final boolean requiresDeduplication;

    /**
     * MessageFormat을 사용하여 제목 생성.
     * 플레이스홀더가 없으면 그대로 반환.
     */
    public String formatTitle(Object... args) {
        if (defaultTitle == null) {
            throw new IllegalStateException("No defaultTitle defined for " + name());
        }
        if (!defaultTitle.contains("{")) {
            return defaultTitle;
        }
        return MessageFormat.format(defaultTitle, args);
    }

    /**
     * MessageFormat을 사용하여 메시지 생성
     */
    public String formatMessage(Object... args) {
        if (messageTemplate == null) {
            throw new IllegalStateException("No messageTemplate defined for " + name());
        }
        if (!messageTemplate.contains("{")) {
            return messageTemplate;
        }
        return MessageFormat.format(messageTemplate, args);
    }

    /**
     * actionUrlPattern에서 {id}를 referenceId로, {0},{1}...를 args로 치환
     */
    public String resolveActionUrl(Long referenceId, Object... args) {
        if (actionUrlPattern == null) return null;
        String url = actionUrlPattern;
        if (referenceId != null) {
            url = url.replace("{id}", referenceId.toString());
        }
        for (int i = 0; i < args.length; i++) {
            url = url.replace("{" + i + "}", args[i] != null ? args[i].toString() : "");
        }
        return url;
    }
}
