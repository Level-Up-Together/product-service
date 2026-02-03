package io.pinkspider.global.kafka;

import io.pinkspider.global.kafka.dto.AppPushMessageDto;
import io.pinkspider.global.kafka.producer.KafkaAppPushProducer;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Kafka를 통한 앱 푸시 알림 통합 테스트
 * <p>
 * 로컬에서 Kafka 메시지를 발행하여 실제 앱에 푸시 알림이 도착하는지 테스트합니다.
 * <p>
 * 테스트 실행 전 필수 확인사항:
 * <ol>
 *   <li>Kafka 서버 연결 가능 (application-test.yml의 bootstrap-servers 확인)</li>
 *   <li>Firebase 설정 활성화 (firebase.enabled: true)</li>
 *   <li>src/main/resources/firebase-service-account.json 파일 존재</li>
 *   <li>테스트할 사용자의 디바이스 토큰이 DB에 등록되어 있어야 함</li>
 * </ol>
 * <p>
 * 테스트 방법:
 * <ol>
 *   <li>USER_ID를 실제 테스트할 사용자 ID로 변경</li>
 *   <li>@Disabled 어노테이션 제거</li>
 *   <li>테스트 실행</li>
 *   <li>해당 사용자의 앱에서 푸시 알림 수신 확인</li>
 * </ol>
 */
@SpringBootTest
@ActiveProfiles("test")
//@Disabled("실제 Kafka/FCM 연동 테스트 - 설정 확인 후 @Disabled 제거")
class KafkaAppPushIntegrationTest {

    // ===============================
    // 테스트 전 아래 값들을 실제 데이터로 변경하세요!
    // ===============================

    // 테스트할 사용자 ID (암호화된 userId)
    private static final String USER_ID = "b663da95-4546-42d2-aecb-898dc21277f0";

    // 여러 사용자 테스트용
    private static final List<String> USER_IDS = List.of(
        "b663da95-4546-42d2-aecb-898dc21277f0"
    );

    // 토픽 기반 전송 테스트용 (예: 길드 ID)
    private static final String TOPIC = "appPushTopic";

    @Autowired
    private KafkaAppPushProducer kafkaAppPushProducer;

    // ===============================
    // 단일 사용자 푸시 테스트
    // ===============================

    @Test
    @Disabled("단일 사용자 푸시 테스트 - USER_ID 설정 후 @Disabled 제거")
    void sendPushToUser_기본알림() throws InterruptedException {
        // Given
        AppPushMessageDto message = AppPushMessageDto.forUser(
            USER_ID,
            "테스트 알림",
            "Kafka를 통한 푸시 알림 테스트입니다!"
        );

        // When
        kafkaAppPushProducer.sendMessage(message);

        // Then - 비동기 전송이므로 잠시 대기
        System.out.println("======================================");
        System.out.println("Kafka 메시지 발행 완료!");
        System.out.println("userId: " + USER_ID);
        System.out.println("앱에서 알림을 확인하세요.");
        System.out.println("======================================");

        waitForKafkaProcessing();
    }

    @Test
    @Disabled("데이터 페이로드 포함 푸시 테스트")
    void sendPushToUser_데이터포함알림() throws InterruptedException {
        // Given
        AppPushMessageDto message = AppPushMessageDto.builder()
            .userId(USER_ID)
            .title("미션 완료!")
            .body("축하합니다! '아침 운동하기' 미션을 완료했습니다.")
            .notificationType("MISSION_COMPLETED")
            .clickAction("/mission/123")
            .data(Map.of(
                "notification_type", "MISSION_COMPLETED",
                "reference_type", "MISSION",
                "reference_id", "123",
                "action_url", "/mission/123"
            ))
            .build();

        // When
        kafkaAppPushProducer.sendMessage(message);

        // Then
        System.out.println("======================================");
        System.out.println("미션 완료 알림 발행 완료!");
        System.out.println("======================================");

        waitForKafkaProcessing();
    }

    @Test
    @Disabled("이미지 포함 푸시 테스트")
    void sendPushToUser_이미지포함알림() throws InterruptedException {
        // Given
        AppPushMessageDto message = AppPushMessageDto.builder()
            .userId(USER_ID)
            .title("새 피드")
            .body("친구님이 새 피드를 올렸습니다.")
            .imageUrl("https://example.com/feed-image.jpg")
            .notificationType("NEW_FEED")
            .clickAction("/feed/456")
            .data(Map.of(
                "notification_type", "NEW_FEED",
                "reference_type", "FEED",
                "reference_id", "456"
            ))
            .build();

        // When
        kafkaAppPushProducer.sendMessage(message);

        // Then
        System.out.println("======================================");
        System.out.println("이미지 포함 알림 발행 완료!");
        System.out.println("======================================");

        waitForKafkaProcessing();
    }

    // ===============================
    // 여러 사용자 푸시 테스트
    // ===============================

    @Test
    @Disabled("여러 사용자 푸시 테스트 - USER_IDS 설정 후 @Disabled 제거")
    void sendPushToUsers_다중사용자알림() throws InterruptedException {
        // Given
        AppPushMessageDto message = AppPushMessageDto.builder()
            .userIds(USER_IDS)
            .title("공지사항")
            .body("새로운 이벤트가 시작되었습니다!")
            .notificationType("ANNOUNCEMENT")
            .data(Map.of(
                "notification_type", "ANNOUNCEMENT",
                "action_url", "/events"
            ))
            .build();

        // When
        kafkaAppPushProducer.sendMessage(message);

        // Then
        System.out.println("======================================");
        System.out.println("다중 사용자 알림 발행 완료!");
        System.out.println("userIds: " + USER_IDS);
        System.out.println("======================================");

        waitForKafkaProcessing();
    }

    // ===============================
    // 토픽 기반 푸시 테스트
    // ===============================

    @Test
    @Disabled("토픽 기반 푸시 테스트 - TOPIC 설정 후 @Disabled 제거")
    void sendPushToTopic_길드채팅알림() throws InterruptedException {
        // Given
        AppPushMessageDto message = AppPushMessageDto.builder()
            .topic(TOPIC)
            .title("길드 채팅")
            .body("새로운 메시지가 도착했습니다.")
            .notificationType("GUILD_CHAT")
            .data(Map.of(
                "notification_type", "GUILD_CHAT",
                "guild_id", "1",
                "action_url", "/guild/1/chat"
            ))
            .build();

        // When
        kafkaAppPushProducer.sendMessage(message);

        // Then
        System.out.println("======================================");
        System.out.println("토픽 기반 알림 발행 완료!");
        System.out.println("topic: " + TOPIC);
        System.out.println("======================================");

        waitForKafkaProcessing();
    }

    // ===============================
    // 실제 알림 시나리오 테스트
    // ===============================

    @Test
    @Disabled("친구 요청 알림 테스트")
    void sendFriendRequestNotification() throws InterruptedException {
        AppPushMessageDto message = AppPushMessageDto.builder()
            .userId(USER_ID)
            .title("새 친구 요청")
            .body("테스트유저님이 친구 요청을 보냈습니다.")
            .notificationType("FRIEND_REQUEST")
            .clickAction("/mypage/friends/requests")
            .data(Map.of(
                "notification_type", "FRIEND_REQUEST",
                "reference_type", "FRIEND_REQUEST",
                "reference_id", "789",
                "sender_nickname", "테스트유저"
            ))
            .build();

        kafkaAppPushProducer.sendMessage(message);
        System.out.println("친구 요청 알림 발행 완료!");
        waitForKafkaProcessing();
    }

    @Test
    @Disabled("길드 초대 알림 테스트")
    void sendGuildInvitationNotification() throws InterruptedException {
        AppPushMessageDto message = AppPushMessageDto.builder()
            .userId(USER_ID)
            .title("길드 초대")
            .body("'테스트 길드'에서 초대장이 도착했습니다.")
            .notificationType("GUILD_INVITATION")
            .clickAction("/guild-invitations")
            .data(Map.of(
                "notification_type", "GUILD_INVITATION",
                "reference_type", "GUILD_INVITATION",
                "reference_id", "101",
                "guild_name", "테스트 길드"
            ))
            .build();

        kafkaAppPushProducer.sendMessage(message);
        System.out.println("길드 초대 알림 발행 완료!");
        waitForKafkaProcessing();
    }

    @Test
    @Disabled("댓글 알림 테스트")
    void sendCommentNotification() throws InterruptedException {
        AppPushMessageDto message = AppPushMessageDto.builder()
            .userId(USER_ID)
            .title("새 댓글")
            .body("친구님이 회원님의 글에 댓글을 남겼습니다: \"좋은 글이네요!\"")
            .notificationType("COMMENT_ON_MY_FEED")
            .clickAction("/feed/456")
            .data(Map.of(
                "notification_type", "COMMENT_ON_MY_FEED",
                "reference_type", "FEED",
                "reference_id", "456",
                "comment_preview", "좋은 글이네요!"
            ))
            .build();

        kafkaAppPushProducer.sendMessage(message);
        System.out.println("댓글 알림 발행 완료!");
        waitForKafkaProcessing();
    }

    @Test
    @Disabled("칭호 획득 알림 테스트")
    void sendTitleAcquiredNotification() throws InterruptedException {
        AppPushMessageDto message = AppPushMessageDto.builder()
            .userId(USER_ID)
            .title("새 칭호 획득!")
            .body("축하합니다! '미션 마스터' 칭호를 획득했습니다.")
            .notificationType("TITLE_ACQUIRED")
            .clickAction("/mypage/titles")
            .data(Map.of(
                "notification_type", "TITLE_ACQUIRED",
                "reference_type", "TITLE",
                "reference_id", "10",
                "title_name", "미션 마스터"
            ))
            .build();

        kafkaAppPushProducer.sendMessage(message);
        System.out.println("칭호 획득 알림 발행 완료!");
        waitForKafkaProcessing();
    }

    @Test
    @Disabled("레벨업 알림 테스트")
    void sendLevelUpNotification() throws InterruptedException {
        AppPushMessageDto message = AppPushMessageDto.builder()
            .userId(USER_ID)
            .title("레벨 업!")
            .body("축하합니다! Lv.10이 되었습니다.")
            .notificationType("LEVEL_UP")
            .clickAction("/mypage")
            .data(Map.of(
                "notification_type", "LEVEL_UP",
                "new_level", "10"
            ))
            .build();

        kafkaAppPushProducer.sendMessage(message);
        System.out.println("레벨업 알림 발행 완료!");
        waitForKafkaProcessing();
    }

    // ===============================
    // 헬퍼 메서드
    // ===============================

    /**
     * Kafka 메시지 처리 대기 Kafka는 비동기로 동작하므로 Consumer가 메시지를 처리할 시간을 줍니다.
     */
    private void waitForKafkaProcessing() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        latch.await(3, TimeUnit.SECONDS);
    }

    // ===============================
    // 유틸리티 테스트
    // ===============================

    @Test
    @Disabled("간편 API 테스트 - sendToUser")
    void sendToUser_간편API() throws InterruptedException {
        kafkaAppPushProducer.sendToUser(
            USER_ID,
            "간편 API 테스트",
            "sendToUser() 메서드 테스트입니다."
        );

        System.out.println("sendToUser 간편 API 테스트 완료!");
        waitForKafkaProcessing();
    }

    @Test
    @Disabled("간편 API 테스트 - sendToTopic")
    void sendToTopic_간편API() throws InterruptedException {
        kafkaAppPushProducer.sendToTopic(
            TOPIC,
            "토픽 알림 테스트",
            "sendToTopic() 메서드 테스트입니다."
        );

        System.out.println("sendToTopic 간편 API 테스트 완료!");
        waitForKafkaProcessing();
    }
}