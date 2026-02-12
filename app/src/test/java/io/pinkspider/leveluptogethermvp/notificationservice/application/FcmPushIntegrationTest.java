package io.pinkspider.leveluptogethermvp.notificationservice.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.AndroidConfig;
import com.google.firebase.messaging.AndroidNotification;
import com.google.firebase.messaging.ApnsConfig;
import com.google.firebase.messaging.Aps;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

/**
 * FCM 푸시 알림 통합 테스트
 * <p>
 * 실제 Firebase로 푸시를 전송하는 테스트입니다. 테스트 실행 전 아래 사항을 확인하세요: 1. src/main/resources/firebase-service-account.json 파일 존재 2. 아래 FCM_TOKEN을 실제 앱에서 발급받은 토큰으로
 * 교체
 *
 * @Disabled 어노테이션을 제거하고 실행하세요.
 */
@Disabled("실제 FCM 테스트 - firebase-service-account.json 필요")
class FcmPushIntegrationTest {

    // ⚠️ 실제 앱에서 발급받은 FCM 토큰으로 교체하세요
//    private static final String FCM_TOKEN = "fCOGOrM-Q0G_5reL31nEd2:APA91bHIIapqUlLNmeS_WhTPDZ6zg3n9BIGDKY_M0XsdPq_j_mEUgrOT63zZZMk32k3CZ0BW6eM5PgHu-f9P1LohgoQknJXLCSrLVnF9JTTqjkYmaObhOdw";
    private static final String FCM_TOKEN = "fCOGOrM-Q0G_5reL31nEd2:APA91bHIIapqUlLNmeS_WhTPDZ6zg3n9BIGDKY_M0XsdPq_j_mEUgrOT63zZZMk32k3CZ0BW6eM5PgHu-f9P1LohgoQknJXLCSrLVnF9JTTqjkYmaObhOdw";

    private static final String SERVICE_ACCOUNT_PATH = "firebase-service-account.json";

    @BeforeAll
    static void initFirebase() throws IOException {
        // 기존 FirebaseApp 인스턴스 모두 삭제 (캐시 문제 방지)
        for (FirebaseApp app : FirebaseApp.getApps()) {
            app.delete();
        }

        ClassPathResource resource = new ClassPathResource(SERVICE_ACCOUNT_PATH);

        // JSON에서 project_id 추출
        ObjectMapper mapper = new ObjectMapper();
        JsonNode jsonNode;
        try (InputStream is = resource.getInputStream()) {
            jsonNode = mapper.readTree(is);
        }
        String projectId = jsonNode.get("project_id").asText();
        System.out.println("Loaded project_id from JSON: " + projectId);

        // Credentials 로드 (FCM scope 명시적 지정)
        GoogleCredentials credentials;
        try (InputStream is = resource.getInputStream()) {
            credentials = GoogleCredentials.fromStream(is)
                .createScoped("https://www.googleapis.com/auth/firebase.messaging",
                    "https://www.googleapis.com/auth/cloud-platform");
        }
        System.out.println("Credentials type: " + credentials.getClass().getSimpleName());

        // 토큰 미리 발급하여 검증
        credentials.refreshIfExpired();
        AccessToken token = credentials.getAccessToken();
        if (token != null) {
            System.out.println("Access token prefix: " + token.getTokenValue().substring(0, 20) + "...");
        }

        FirebaseOptions options = FirebaseOptions.builder()
            .setCredentials(credentials)
            .setProjectId(projectId)  // 명시적으로 project ID 설정
            .build();

        FirebaseApp.initializeApp(options);
        System.out.println("✅ Firebase initialized successfully");
        System.out.println("Project ID: " + FirebaseApp.getInstance().getOptions().getProjectId());
    }

    @Test
//    @Disabled("실제 푸시 전송 테스트 - FCM_TOKEN 설정 후 @Disabled 제거")
    void sendPushNotification_실제전송테스트() {
        try {
            // Given
            Message message = Message.builder()
                .setToken(FCM_TOKEN)
                .setNotification(Notification.builder()
                    .setTitle("테스트 알림 🔔")
                    .setBody("Level Up Together 푸시 알림 테스트입니다!")
                    .build())
                .putAllData(Map.of(
                    "notification_type", "TEST",
                    "click_action", "/home"
                ))
                // iOS 설정
                .setApnsConfig(ApnsConfig.builder()
                    .setAps(Aps.builder()
                        .setBadge(1)
                        .setSound("default")
                        .build())
                    .build())
                // Android 설정
                .setAndroidConfig(AndroidConfig.builder()
                    .setNotification(AndroidNotification.builder()
                        .setSound("default")
                        .build())
                    .build())
                .build();

            // When
            String response = FirebaseMessaging.getInstance().send(message);

            // Then
            System.out.println("✅ 푸시 전송 성공!");
            System.out.println("Response: " + response);
        } catch (FirebaseMessagingException e) {
            System.out.println("❌ FCM 전송 실패!");
            System.out.println("Error Code: " + e.getMessagingErrorCode());
            System.out.println("HTTP Response: " + e.getHttpResponse());
            System.out.println("Message: " + e.getMessage());
            if (e.getCause() != null) {
                System.out.println("Cause: " + e.getCause().getMessage());
            }
            throw new RuntimeException(e);
        }
    }

    @Test
    @Disabled("친구 요청 알림 테스트 - FCM_TOKEN 설정 후 @Disabled 제거")
    void sendFriendRequestNotification() throws FirebaseMessagingException {
        Message message = Message.builder()
            .setToken(FCM_TOKEN)
            .setNotification(Notification.builder()
                .setTitle("새 친구 요청")
                .setBody("테스트유저님이 친구 요청을 보냈습니다.")
                .build())
            .putAllData(Map.of(
                "notification_type", "FRIEND_REQUEST",
                "reference_type", "FRIEND_REQUEST",
                "reference_id", "123",
                "action_url", "/mypage/friends/requests"
            ))
            .setApnsConfig(ApnsConfig.builder()
                .setAps(Aps.builder()
                    .setBadge(1)
                    .setSound("default")
                    .build())
                .build())
            .build();

        String response = FirebaseMessaging.getInstance().send(message);
        System.out.println("✅ 친구 요청 알림 전송 성공: " + response);
    }

    @Test
    @Disabled("길드 채팅 알림 테스트 - FCM_TOKEN 설정 후 @Disabled 제거")
    void sendGuildChatNotification() throws FirebaseMessagingException {
        Message message = Message.builder()
            .setToken(FCM_TOKEN)
            .setNotification(Notification.builder()
                .setTitle("테스트 길드")
                .setBody("길드원: 안녕하세요! 오늘 미션 같이 해요~")
                .build())
            .putAllData(Map.of(
                "notification_type", "GUILD_CHAT",
                "reference_type", "GUILD_CHAT",
                "guild_id", "1",
                "action_url", "/guild/1/chat"
            ))
            .setApnsConfig(ApnsConfig.builder()
                .setAps(Aps.builder()
                    .setBadge(3)
                    .setSound("default")
                    .build())
                .build())
            .build();

        String response = FirebaseMessaging.getInstance().send(message);
        System.out.println("✅ 길드 채팅 알림 전송 성공: " + response);
    }

    @Test
    @Disabled("댓글 알림 테스트 - FCM_TOKEN 설정 후 @Disabled 제거")
    void sendCommentNotification() throws FirebaseMessagingException {
        Message message = Message.builder()
            .setToken(FCM_TOKEN)
            .setNotification(Notification.builder()
                .setTitle("새 댓글")
                .setBody("친구님이 회원님의 글에 댓글을 남겼습니다.")
                .build())
            .putAllData(Map.of(
                "notification_type", "COMMENT_ON_MY_FEED",
                "reference_type", "FEED",
                "reference_id", "456",
                "action_url", "/feed/456"
            ))
            .setApnsConfig(ApnsConfig.builder()
                .setAps(Aps.builder()
                    .setBadge(2)
                    .setSound("default")
                    .build())
                .build())
            .build();

        String response = FirebaseMessaging.getInstance().send(message);
        System.out.println("✅ 댓글 알림 전송 성공: " + response);
    }

    @Test
    void sendFcmWithRawHttp() throws IOException {
        // 서비스 계정 자격 증명 로드
        ClassPathResource resource = new ClassPathResource(SERVICE_ACCOUNT_PATH);
        ObjectMapper mapper = new ObjectMapper();
        JsonNode jsonNode;
        try (InputStream is = resource.getInputStream()) {
            jsonNode = mapper.readTree(is);
        }
        String projectId = jsonNode.get("project_id").asText();

        GoogleCredentials credentials;
        try (InputStream is = resource.getInputStream()) {
            credentials = GoogleCredentials.fromStream(is)
                .createScoped(
                    "https://www.googleapis.com/auth/firebase.messaging",
                    "https://www.googleapis.com/auth/cloud-platform"
                );
        }
        credentials.refreshIfExpired();
        String accessToken = credentials.getAccessToken().getTokenValue();

        System.out.println("=== Raw HTTP FCM Test ===");
        System.out.println("Project ID: " + projectId);
        System.out.println("Token prefix: " + accessToken.substring(0, 30) + "...");
        System.out.println("Full access token (for curl test): " + accessToken);

        // FCM v1 API URL
        String fcmUrl = "https://fcm.googleapis.com/v1/projects/" + projectId + "/messages:send";
        System.out.println("FCM URL: " + fcmUrl);

        // curl 명령어 출력
        System.out.println("\n=== Copy this curl command to test manually ===");
        System.out.println("curl -X POST '" + fcmUrl + "' \\");
        System.out.println("  -H 'Authorization: Bearer " + accessToken + "' \\");
        System.out.println("  -H 'Content-Type: application/json' \\");
        System.out.println("  -d '{\"message\":{\"token\":\"" + FCM_TOKEN + "\",\"notification\":{\"title\":\"Test\",\"body\":\"Test\"}}}'");
        System.out.println("=== End of curl command ===\n");

        // JSON body
        String jsonBody = """
            {
              "message": {
                "token": "%s",
                "notification": {
                  "title": "Raw HTTP Test",
                  "body": "Testing FCM with raw HTTP request"
                }
              }
            }
            """.formatted(FCM_TOKEN);

        // HTTP 요청
        URL url = new URL(fcmUrl);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Authorization", "Bearer " + accessToken);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setDoOutput(true);

        try (OutputStream os = conn.getOutputStream()) {
            os.write(jsonBody.getBytes(StandardCharsets.UTF_8));
        }

        int responseCode = conn.getResponseCode();
        System.out.println("Response Code: " + responseCode);

        InputStream responseStream = (responseCode >= 200 && responseCode < 300)
            ? conn.getInputStream()
            : conn.getErrorStream();

        if (responseStream != null) {
            String response = new String(responseStream.readAllBytes(), StandardCharsets.UTF_8);
            System.out.println("Response Body: " + response);
        }

        if (responseCode == 200) {
            System.out.println("✅ Raw HTTP FCM 전송 성공!");
        } else {
            System.out.println("❌ Raw HTTP FCM 전송 실패!");
        }
    }

    @Test
    void verifyAccessToken() throws IOException {
        // 서비스 계정 자격 증명 로드
        ClassPathResource resource = new ClassPathResource(SERVICE_ACCOUNT_PATH);
        GoogleCredentials credentials;
        try (InputStream is = resource.getInputStream()) {
            credentials = GoogleCredentials.fromStream(is)
                .createScoped(
                    "https://www.googleapis.com/auth/firebase.messaging",
                    "https://www.googleapis.com/auth/cloud-platform"
                );
        }

        // 명시적으로 토큰 갱신
        credentials.refreshIfExpired();
        AccessToken accessToken = credentials.getAccessToken();

        System.out.println("=== Access Token Test ===");
        System.out.println("Access Token: " + (accessToken != null ?
            accessToken.getTokenValue().substring(0, 20) + "..." : "null"));
        System.out.println("Expiration: " + (accessToken != null ? accessToken.getExpirationTime() : "null"));

        if (accessToken != null && accessToken.getTokenValue() != null) {
            System.out.println("✅ Access token obtained successfully!");

            // Cloud Resource Manager API로 토큰 유효성 검증
            String testUrl = "https://cloudresourcemanager.googleapis.com/v1/projects/level-up-together-dev-486205";
            URL url = new URL(testUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Authorization", "Bearer " + accessToken.getTokenValue());

            int responseCode = conn.getResponseCode();
            System.out.println("Token validation test - Response Code: " + responseCode);

            if (responseCode != 200) {
                InputStream errorStream = conn.getErrorStream();
                if (errorStream != null) {
                    String error = new String(errorStream.readAllBytes(), StandardCharsets.UTF_8);
                    System.out.println("Error: " + error);
                }
            }
        } else {
            System.out.println("❌ Failed to get access token");
        }
    }

    @Test
    void checkFirebaseApps() throws IOException, InterruptedException {
        // Firebase 앱 목록 확인
        System.out.println("=== Firebase Apps Check ===");

        ClassPathResource resource = new ClassPathResource(SERVICE_ACCOUNT_PATH);
        GoogleCredentials credentials;
        try (InputStream is = resource.getInputStream()) {
            credentials = GoogleCredentials.fromStream(is)
                .createScoped("https://www.googleapis.com/auth/cloud-platform");
        }
        credentials.refreshIfExpired();
        String accessToken = credentials.getAccessToken().getTokenValue();

        HttpClient client = HttpClient.newHttpClient();

        // Android 앱 목록
        HttpRequest androidRequest = HttpRequest.newBuilder()
            .uri(URI.create("https://firebase.googleapis.com/v1beta1/projects/level-up-together-dev-486205/androidApps"))
            .header("Authorization", "Bearer " + accessToken)
            .GET()
            .build();

        HttpResponse<String> androidResponse = client.send(androidRequest, HttpResponse.BodyHandlers.ofString());
        System.out.println("Android Apps: " + androidResponse.body());

        // iOS 앱 목록
        HttpRequest iosRequest = HttpRequest.newBuilder()
            .uri(URI.create("https://firebase.googleapis.com/v1beta1/projects/level-up-together-dev-486205/iosApps"))
            .header("Authorization", "Bearer " + accessToken)
            .GET()
            .build();

        HttpResponse<String> iosResponse = client.send(iosRequest, HttpResponse.BodyHandlers.ofString());
        System.out.println("iOS Apps: " + iosResponse.body());
    }

    @Test
    void sendFcmWithHttpClient() throws IOException, InterruptedException {
        // Java 11+ HttpClient를 사용한 FCM v1 테스트
        System.out.println("=== HttpClient FCM Test ===");

        ClassPathResource resource = new ClassPathResource(SERVICE_ACCOUNT_PATH);
        ObjectMapper mapper = new ObjectMapper();
        JsonNode jsonNode;
        try (InputStream is = resource.getInputStream()) {
            jsonNode = mapper.readTree(is);
        }
        String projectId = jsonNode.get("project_id").asText();

        GoogleCredentials credentials;
        try (InputStream is = resource.getInputStream()) {
            credentials = GoogleCredentials.fromStream(is)
                .createScoped(
                    "https://www.googleapis.com/auth/firebase.messaging",
                    "https://www.googleapis.com/auth/cloud-platform"
                );
        }
        credentials.refreshIfExpired();
        String accessToken = credentials.getAccessToken().getTokenValue();

        System.out.println("Access Token: " + accessToken.substring(0, 30) + "...");

        String fcmUrl = "https://fcm.googleapis.com/v1/projects/" + projectId + "/messages:send";
        String jsonBody = """
            {
              "message": {
                "token": "%s",
                "notification": {
                  "title": "HttpClient Test",
                  "body": "Testing with Java HttpClient"
                }
              }
            }
            """.formatted(FCM_TOKEN);

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(fcmUrl))
            .header("Authorization", "Bearer " + accessToken)
            .header("Content-Type", "application/json; charset=UTF-8")
            .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
            .build();

        // 요청 헤더 출력
        System.out.println("Request URL: " + fcmUrl);
        System.out.println("Request Headers:");
        request.headers().map().forEach((k, v) -> System.out.println("  " + k + ": " + v));

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        System.out.println("Response Status: " + response.statusCode());
        System.out.println("Response Body: " + response.body());

        if (response.statusCode() == 200) {
            System.out.println("✅ HttpClient FCM 전송 성공!");
        } else {
            System.out.println("❌ HttpClient FCM 전송 실패!");
        }
    }

    @Test
    void testLegacyFcmApi() throws IOException {
        // Legacy FCM HTTP API 테스트 (Server Key 사용)
        // Firebase Console > Project Settings > Cloud Messaging > Server key
        System.out.println("=== Legacy FCM API Test ===");
        System.out.println("Note: This requires the Server Key from Firebase Console");
        System.out.println("Go to: https://console.firebase.google.com/project/level-up-together-dev-486205/settings/cloudmessaging");
        System.out.println("Copy the 'Server key' and set it below");

        // 서비스 계정으로 Cloud Messaging API 정보 조회 테스트
        ClassPathResource resource = new ClassPathResource(SERVICE_ACCOUNT_PATH);
        GoogleCredentials credentials;
        try (InputStream is = resource.getInputStream()) {
            credentials = GoogleCredentials.fromStream(is)
                .createScoped("https://www.googleapis.com/auth/cloud-platform");
        }
        credentials.refreshIfExpired();
        String accessToken = credentials.getAccessToken().getTokenValue();

        // Firebase Cloud Messaging API 상태 확인
        // 1. 먼저 Firebase project 확인
        String apiCheckUrl = "https://firebase.googleapis.com/v1beta1/projects/level-up-together-dev-486205";

        // 2. GCP Service Usage API로 FCM API 활성화 상태 확인
        String fcmApiCheckUrl = "https://serviceusage.googleapis.com/v1/projects/level-up-together-dev-486205/services/fcm.googleapis.com";
        URL fcmUrl = new URL(fcmApiCheckUrl);
        HttpURLConnection fcmConn = (HttpURLConnection) fcmUrl.openConnection();
        fcmConn.setRequestMethod("GET");
        fcmConn.setRequestProperty("Authorization", "Bearer " + accessToken);

        System.out.println("FCM API Status Check - Response Code: " + fcmConn.getResponseCode());
        InputStream fcmStream = (fcmConn.getResponseCode() >= 200 && fcmConn.getResponseCode() < 300)
            ? fcmConn.getInputStream()
            : fcmConn.getErrorStream();
        if (fcmStream != null) {
            String fcmResponse = new String(fcmStream.readAllBytes(), StandardCharsets.UTF_8);
            System.out.println("FCM API Status: " + fcmResponse);
        }
        URL url = new URL(apiCheckUrl);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Authorization", "Bearer " + accessToken);

        int responseCode = conn.getResponseCode();
        System.out.println("Firebase Project API Check - Response Code: " + responseCode);

        InputStream responseStream = (responseCode >= 200 && responseCode < 300)
            ? conn.getInputStream()
            : conn.getErrorStream();

        if (responseStream != null) {
            String response = new String(responseStream.readAllBytes(), StandardCharsets.UTF_8);
            System.out.println("Response: " + response.substring(0, Math.min(500, response.length())));
        }
    }

    @Test
    @Disabled("토큰 유효성 검사 - FCM_TOKEN 설정 후 @Disabled 제거")
    void validateToken() {
        try {
            // Dry run으로 토큰 유효성만 검사 (실제 전송 안 함)
            Message message = Message.builder()
                .setToken(FCM_TOKEN)
                .setNotification(Notification.builder()
                    .setTitle("Test")
                    .setBody("Test")
                    .build())
                .build();

            String response = FirebaseMessaging.getInstance().send(message, true); // dryRun = true
            System.out.println("✅ 토큰 유효함: " + response);
        } catch (FirebaseMessagingException e) {
            System.out.println("❌ 토큰 무효: " + e.getMessagingErrorCode());
            System.out.println("Error: " + e.getMessage());
        }
    }
}
