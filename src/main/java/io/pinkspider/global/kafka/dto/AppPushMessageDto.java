package io.pinkspider.global.kafka.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AppPushMessageDto {

    @JsonProperty("template_id")
    private String templateId;

    @JsonProperty("user_id")
    private String userId;

    @JsonProperty("user_ids")
    private List<String> userIds;

    @JsonProperty("title")
    private String title;

    @JsonProperty("body")
    private String body;

    @JsonProperty("image_url")
    private String imageUrl;

    @JsonProperty("click_action")
    private String clickAction;

    @JsonProperty("data")
    private Map<String, String> data;

    @JsonProperty("topic")
    private String topic;

    @JsonProperty("notification_type")
    private String notificationType;

    /**
     * 단일 사용자 푸시
     */
    public static AppPushMessageDto forUser(String userId, String title, String body) {
        return AppPushMessageDto.builder()
                .userId(userId)
                .title(title)
                .body(body)
                .build();
    }

    /**
     * 여러 사용자 푸시
     */
    public static AppPushMessageDto forUsers(List<String> userIds, String title, String body) {
        return AppPushMessageDto.builder()
                .userIds(userIds)
                .title(title)
                .body(body)
                .build();
    }

    /**
     * 토픽 푸시 (길드 등)
     */
    public static AppPushMessageDto forTopic(String topic, String title, String body) {
        return AppPushMessageDto.builder()
                .topic(topic)
                .title(title)
                .body(body)
                .build();
    }
}
