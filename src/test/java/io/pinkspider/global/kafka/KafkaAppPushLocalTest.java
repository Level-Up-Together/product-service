package io.pinkspider.global.kafka;

import io.pinkspider.global.kafka.dto.AppPushMessageDto;
import io.pinkspider.global.kafka.producer.KafkaAppPushProducer;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * 로컬에서 Kafka 앱 푸시 테스트
 * <p>
 * push-test 프로필을 사용하여 실제 Kafka와 Firebase에 연결하여 테스트합니다.
 * <p>
 * 실행 방법:
 * <pre>
 * # 단일 테스트 실행
 * ./gradlew test --tests "*.KafkaAppPushLocalTest.sendPushToUser_단일사용자테스트"
 *
 * # IDE에서 실행
 * 1. @Disabled 제거
 * 2. USER_ID 설정
 * 3. 테스트 메서드 우클릭 > Run
 * </pre>
 * <p>
 * 필수 조건:
 * <ul>
 *   <li>Kafka 서버 연결 가능 (121.136.108.44:9092)</li>
 *   <li>firebase-service-account.json 존재</li>
 *   <li>테스트 대상 사용자의 디바이스 토큰이 notification_db에 등록되어 있어야 함</li>
 * </ul>
 */
@SpringBootTest
@ActiveProfiles({"test", "push-test"})
//@Disabled("로컬 푸시 테스트 - USER_ID 설정 후 @Disabled 제거하고 실행")
class KafkaAppPushLocalTest {

    // =====================================================
    // 여기에 테스트할 사용자 ID를 입력하세요!
    // =====================================================
    private static final String USER_ID = "b663da95-4546-42d2-aecb-898dc21277f0";
    // =====================================================

    @Autowired
    private KafkaAppPushProducer kafkaAppPushProducer;

    /**
     * 가장 간단한 푸시 테스트
     */
    @Test
    void sendPushToUser_단일사용자테스트() throws InterruptedException {
        kafkaAppPushProducer.sendToUser(
            USER_ID,
            "테스트 알림",
            "로컬에서 Kafka를 통해 보낸 푸시 알림입니다!"
        );

        printSuccess("단일 사용자 푸시");
        waitForProcessing();
    }

    /**
     * 데이터 페이로드를 포함한 푸시 테스트
     */
    @Test
    void sendPushWithData_데이터포함테스트() throws InterruptedException {
        AppPushMessageDto message = AppPushMessageDto.builder()
            .userId(USER_ID)
            .title("미션 완료!")
            .body("축하합니다! 미션을 성공적으로 완료했습니다.")
            .notificationType("MISSION_COMPLETED")
            .clickAction("/mission/1")
            .data(Map.of(
                "notification_type", "MISSION_COMPLETED",
                "mission_id", "1",
                "action_url", "/mission/1"
            ))
            .build();

        kafkaAppPushProducer.sendMessage(message);

        printSuccess("데이터 포함 푸시");
        waitForProcessing();
    }

    /**
     * 친구 요청 알림 테스트
     */
    @Test
    void sendFriendRequest_친구요청알림() throws InterruptedException {
        AppPushMessageDto message = AppPushMessageDto.builder()
            .userId(USER_ID)
            .title("새 친구 요청")
            .body("홍길동님이 친구 요청을 보냈습니다.")
            .notificationType("FRIEND_REQUEST")
            .data(Map.of(
                "notification_type", "FRIEND_REQUEST",
                "sender_id", "test-sender-id",
                "sender_nickname", "홍길동"
            ))
            .build();

        kafkaAppPushProducer.sendMessage(message);

        printSuccess("친구 요청 알림");
        waitForProcessing();
    }

    /**
     * 길드 초대 알림 테스트
     */
    @Test
    void sendGuildInvitation_길드초대알림() throws InterruptedException {
        AppPushMessageDto message = AppPushMessageDto.builder()
            .userId(USER_ID)
            .title("길드 초대")
            .body("'레벨업 길드'에서 초대장이 도착했습니다.")
            .notificationType("GUILD_INVITATION")
            .data(Map.of(
                "notification_type", "GUILD_INVITATION",
                "guild_id", "1",
                "guild_name", "레벨업 길드"
            ))
            .build();

        kafkaAppPushProducer.sendMessage(message);

        printSuccess("길드 초대 알림");
        waitForProcessing();
    }

    /**
     * 댓글 알림 테스트
     */
    @Test
    void sendComment_댓글알림() throws InterruptedException {
        AppPushMessageDto message = AppPushMessageDto.builder()
            .userId(USER_ID)
            .title("새 댓글")
            .body("친구님이 댓글을 남겼습니다: \"좋은 글이네요!\"")
            .notificationType("COMMENT_ON_MY_FEED")
            .data(Map.of(
                "notification_type", "COMMENT_ON_MY_FEED",
                "feed_id", "123",
                "comment_preview", "좋은 글이네요!"
            ))
            .build();

        kafkaAppPushProducer.sendMessage(message);

        printSuccess("댓글 알림");
        waitForProcessing();
    }

    /**
     * 좋아요 알림 테스트
     */
    @Test
    void sendLike_좋아요알림() throws InterruptedException {
        AppPushMessageDto message = AppPushMessageDto.builder()
            .userId(USER_ID)
            .title("새 좋아요")
            .body("친구님이 회원님의 글을 좋아합니다.")
            .notificationType("LIKE_ON_MY_FEED")
            .data(Map.of(
                "notification_type", "LIKE_ON_MY_FEED",
                "feed_id", "123"
            ))
            .build();

        kafkaAppPushProducer.sendMessage(message);

        printSuccess("좋아요 알림");
        waitForProcessing();
    }

    /**
     * 칭호 획득 알림 테스트
     */
    @Test
    void sendTitleAcquired_칭호획득알림() throws InterruptedException {
        AppPushMessageDto message = AppPushMessageDto.builder()
            .userId(USER_ID)
            .title("새 칭호 획득!")
            .body("축하합니다! '미션 마스터' 칭호를 획득했습니다.")
            .notificationType("TITLE_ACQUIRED")
            .data(Map.of(
                "notification_type", "TITLE_ACQUIRED",
                "title_id", "10",
                "title_name", "미션 마스터"
            ))
            .build();

        kafkaAppPushProducer.sendMessage(message);

        printSuccess("칭호 획득 알림");
        waitForProcessing();
    }

    /**
     * 레벨업 알림 테스트
     */
    @Test
    void sendLevelUp_레벨업알림() throws InterruptedException {
        AppPushMessageDto message = AppPushMessageDto.builder()
            .userId(USER_ID)
            .title("레벨 업!")
            .body("축하합니다! Lv.10이 되었습니다.")
            .notificationType("LEVEL_UP")
            .data(Map.of(
                "notification_type", "LEVEL_UP",
                "new_level", "10"
            ))
            .build();

        kafkaAppPushProducer.sendMessage(message);

        printSuccess("레벨업 알림");
        waitForProcessing();
    }

    // =====================================================
    // Helper Methods
    // =====================================================

    private void printSuccess(String testName) {
        System.out.println();
        System.out.println("========================================");
        System.out.println(" [" + testName + "] Kafka 메시지 발행 완료!");
        System.out.println(" userId: " + USER_ID);
        System.out.println(" 앱에서 푸시 알림을 확인하세요.");
        System.out.println("========================================");
        System.out.println();
    }

    private void waitForProcessing() throws InterruptedException {
        // Kafka Consumer가 메시지를 처리하고 FCM으로 전송할 시간 대기
        new CountDownLatch(1).await(5, TimeUnit.SECONDS);
    }
}
