package io.pinkspider.global.config.firebase;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Firebase Admin SDK 설정
 */
@Slf4j
@Configuration
public class FirebaseConfig {

    @Value("${firebase.credentials.path:firebase-service-account.json}")
    private String credentialsPath;

    @Value("${firebase.credentials.json:}")
    private String credentialsJson;

    @Value("${firebase.enabled:false}")
    private boolean firebaseEnabled;

    @PostConstruct
    public void initialize() {
        if (!firebaseEnabled) {
            log.info("Firebase is disabled. Skipping initialization.");
            return;
        }

        try {
            if (FirebaseApp.getApps().isEmpty()) {
                InputStream serviceAccount = getCredentialsInputStream();

                if (serviceAccount == null) {
                    log.warn("Firebase credentials not found. Push notifications will be disabled.");
                    return;
                }

                FirebaseOptions options = FirebaseOptions.builder()
                        .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                        .build();

                FirebaseApp.initializeApp(options);
                log.info("Firebase Admin SDK initialized successfully.");
            }
        } catch (IOException e) {
            log.error("Failed to initialize Firebase Admin SDK", e);
        }
    }

    private InputStream getCredentialsInputStream() throws IOException {
        // 환경 변수로 JSON 직접 제공된 경우 (배포 환경)
        if (credentialsJson != null && !credentialsJson.isEmpty()) {
            return new ByteArrayInputStream(credentialsJson.getBytes(StandardCharsets.UTF_8));
        }

        // classpath에서 파일 로드 (로컬 개발 환경)
        try {
            Resource resource = new ClassPathResource(credentialsPath);
            if (resource.exists()) {
                return resource.getInputStream();
            }
        } catch (Exception e) {
            log.debug("Could not load Firebase credentials from classpath: {}", credentialsPath);
        }

        return null;
    }

    @Bean
    public FirebaseMessaging firebaseMessaging() {
        if (!firebaseEnabled || FirebaseApp.getApps().isEmpty()) {
            log.warn("FirebaseMessaging bean not created - Firebase is not initialized.");
            return null;
        }
        return FirebaseMessaging.getInstance();
    }
}
