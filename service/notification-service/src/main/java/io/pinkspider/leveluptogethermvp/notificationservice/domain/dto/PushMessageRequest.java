package io.pinkspider.leveluptogethermvp.notificationservice.domain.dto;

import java.util.Map;

/**
 * 푸시 알림 메시지 요청 DTO
 */
public record PushMessageRequest(
        String title,
        String body,
        String imageUrl,
        String clickAction,
        Map<String, String> data
) {
    /**
     * 기본 알림 생성
     */
    public static PushMessageRequest of(String title, String body) {
        return new PushMessageRequest(title, body, null, null, null);
    }

    /**
     * 데이터 포함 알림 생성
     */
    public static PushMessageRequest of(String title, String body, Map<String, String> data) {
        return new PushMessageRequest(title, body, null, null, data);
    }

    /**
     * 이미지 포함 알림 생성
     */
    public static PushMessageRequest withImage(String title, String body, String imageUrl) {
        return new PushMessageRequest(title, body, imageUrl, null, null);
    }

    /**
     * 전체 옵션 알림 생성
     */
    public static PushMessageRequest full(String title, String body, String imageUrl,
                                          String clickAction, Map<String, String> data) {
        return new PushMessageRequest(title, body, imageUrl, clickAction, data);
    }
}
