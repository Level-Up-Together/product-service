# Notification DB 분리 마이그레이션 계획

## 1. 개요

### 1.1 목적
- `user_db`에서 알림 관련 테이블을 `notification_db`로 분리
- MSA 전환 대비 서비스 경계 명확화
- 알림 서비스의 독립적 스케일링 가능

### 1.2 범위
| 항목 | 내용 |
|---|---|
| 이동 대상 테이블 | `notification`, `notification_preference` |
| 현재 위치 | `user_db` |
| 목표 위치 | `notification_db` (신규) |
| 데이터 건수 | notification: 15건 (현재 소량) |

### 1.3 영향 분석
- **영향 서비스**: NotificationService, NotificationEventListener
- **API 엔드포인트**: `/api/v1/notifications/**`
- **의존성**:
  - user_id로 사용자 참조 (String, FK 없음)
  - 다른 서비스에서 NotificationService 호출
- **위험도**: 낮음 (독립적인 테이블, FK 없음)

---

## 2. 현재 구조

### 2.1 테이블 스키마

```sql
-- notification 테이블
CREATE TABLE notification (
    id BIGSERIAL PRIMARY KEY,
    user_id VARCHAR(255) NOT NULL,
    notification_type VARCHAR(30) NOT NULL,
    title VARCHAR(100) NOT NULL,
    message VARCHAR(500),
    reference_type VARCHAR(30),
    reference_id BIGINT,
    action_url VARCHAR(500),
    icon_url VARCHAR(500),
    is_read BOOLEAN DEFAULT FALSE,
    read_at TIMESTAMP,
    is_pushed BOOLEAN DEFAULT FALSE,
    pushed_at TIMESTAMP,
    expires_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    modified_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_notification_user ON notification(user_id);
CREATE INDEX idx_notification_user_read ON notification(user_id, is_read);
CREATE INDEX idx_notification_created ON notification(created_at DESC);

-- notification_preference 테이블
CREATE TABLE notification_preference (
    id BIGSERIAL PRIMARY KEY,
    user_id VARCHAR(255) NOT NULL UNIQUE,
    push_enabled BOOLEAN DEFAULT TRUE,
    friend_notifications BOOLEAN DEFAULT TRUE,
    guild_notifications BOOLEAN DEFAULT TRUE,
    social_notifications BOOLEAN DEFAULT TRUE,
    system_notifications BOOLEAN DEFAULT TRUE,
    quiet_hours_enabled BOOLEAN DEFAULT FALSE,
    quiet_hours_start VARCHAR(10),
    quiet_hours_end VARCHAR(10),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    modified_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX idx_notification_pref_user ON notification_preference(user_id);
```

### 2.2 현재 코드 구조

```
io.pinkspider.leveluptogethermvp.userservice.notification/
├── api/
│   └── NotificationController.java
├── application/
│   └── NotificationService.java
├── domain/
│   ├── entity/
│   │   ├── Notification.java
│   │   └── NotificationPreference.java
│   ├── dto/
│   │   ├── NotificationResponse.java
│   │   ├── NotificationSummaryResponse.java
│   │   ├── NotificationPreferenceRequest.java
│   │   └── NotificationPreferenceResponse.java
│   └── enums/
│       └── NotificationType.java
└── infrastructure/
    ├── NotificationRepository.java
    └── NotificationPreferenceRepository.java
```

### 2.3 현재 트랜잭션 매니저
- `userTransactionManager` (Primary) 사용 중
- `@Transactional` 어노테이션에 명시적 지정 없음

---

## 3. 목표 구조

### 3.1 신규 패키지 구조

```
io.pinkspider.leveluptogethermvp.notificationservice/  # 신규 서비스 패키지
├── api/
│   └── NotificationController.java
├── application/
│   └── NotificationService.java
├── domain/
│   ├── entity/
│   │   ├── Notification.java
│   │   └── NotificationPreference.java
│   ├── dto/
│   │   └── ...
│   └── enums/
│       └── NotificationType.java
└── infrastructure/
    ├── NotificationRepository.java
    └── NotificationPreferenceRepository.java

io.pinkspider.leveluptogethermvp.notificationservice.core.properties/
└── NotificationDataSourceProperties.java

io.pinkspider.global.config.datasource/
└── NotificationDataSourceConfig.java  # 신규
```

### 3.2 신규 DataSource 설정

```yaml
# config-repository/level-up-together-mvp/application-dev.yml
spring:
  datasource:
    notification:  # 신규
      jdbc-url: jdbc:log4jdbc:postgresql://localhost:5433/notification_db?sslmode=disable&TimeZone=Asia/Seoul
      username: "{cipher}..."
      password: "{cipher}..."
      driver-class-name: net.sf.log4jdbc.sql.jdbcapi.DriverSpy
```

---

## 4. 마이그레이션 단계

### Phase 1: 인프라 준비 (1일)

#### 4.1.1 신규 데이터베이스 생성

```sql
-- PostgreSQL에서 실행
CREATE DATABASE notification_db
    WITH
    OWNER = postgres
    ENCODING = 'UTF8'
    LC_COLLATE = 'en_US.UTF-8'
    LC_CTYPE = 'en_US.UTF-8'
    TABLESPACE = pg_default
    CONNECTION LIMIT = -1;

-- 사용자 권한 부여
GRANT ALL PRIVILEGES ON DATABASE notification_db TO level_up_user;
```

#### 4.1.2 테이블 생성 (notification_db)

```sql
-- notification_db에서 실행

-- notification 테이블
CREATE TABLE notification (
    id BIGSERIAL PRIMARY KEY,
    user_id VARCHAR(255) NOT NULL,
    notification_type VARCHAR(30) NOT NULL,
    title VARCHAR(100) NOT NULL,
    message VARCHAR(500),
    reference_type VARCHAR(30),
    reference_id BIGINT,
    action_url VARCHAR(500),
    icon_url VARCHAR(500),
    is_read BOOLEAN DEFAULT FALSE,
    read_at TIMESTAMP,
    is_pushed BOOLEAN DEFAULT FALSE,
    pushed_at TIMESTAMP,
    expires_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    modified_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_notification_user ON notification(user_id);
CREATE INDEX idx_notification_user_read ON notification(user_id, is_read);
CREATE INDEX idx_notification_created ON notification(created_at DESC);

COMMENT ON TABLE notification IS '알림';
COMMENT ON COLUMN notification.id IS 'ID';
COMMENT ON COLUMN notification.user_id IS '사용자 ID';
COMMENT ON COLUMN notification.notification_type IS '알림 타입';
COMMENT ON COLUMN notification.title IS '알림 제목';
COMMENT ON COLUMN notification.message IS '알림 메시지';
COMMENT ON COLUMN notification.reference_type IS '참조 타입 (MISSION, ACHIEVEMENT, GUILD 등)';
COMMENT ON COLUMN notification.reference_id IS '참조 ID';
COMMENT ON COLUMN notification.action_url IS '클릭 시 이동 URL';
COMMENT ON COLUMN notification.icon_url IS '아이콘 URL';
COMMENT ON COLUMN notification.is_read IS '읽음 여부';
COMMENT ON COLUMN notification.read_at IS '읽은 시간';
COMMENT ON COLUMN notification.is_pushed IS '푸시 발송 여부';
COMMENT ON COLUMN notification.pushed_at IS '푸시 발송 시간';
COMMENT ON COLUMN notification.expires_at IS '만료 시간';

-- notification_preference 테이블
CREATE TABLE notification_preference (
    id BIGSERIAL PRIMARY KEY,
    user_id VARCHAR(255) NOT NULL UNIQUE,
    push_enabled BOOLEAN DEFAULT TRUE,
    friend_notifications BOOLEAN DEFAULT TRUE,
    guild_notifications BOOLEAN DEFAULT TRUE,
    social_notifications BOOLEAN DEFAULT TRUE,
    system_notifications BOOLEAN DEFAULT TRUE,
    quiet_hours_enabled BOOLEAN DEFAULT FALSE,
    quiet_hours_start VARCHAR(10),
    quiet_hours_end VARCHAR(10),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    modified_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX idx_notification_pref_user ON notification_preference(user_id);

COMMENT ON TABLE notification_preference IS '알림 설정';
COMMENT ON COLUMN notification_preference.user_id IS '사용자 ID';
COMMENT ON COLUMN notification_preference.push_enabled IS '푸시 알림 활성화';
COMMENT ON COLUMN notification_preference.friend_notifications IS '친구 알림';
COMMENT ON COLUMN notification_preference.guild_notifications IS '길드 알림';
COMMENT ON COLUMN notification_preference.social_notifications IS '소셜 알림';
COMMENT ON COLUMN notification_preference.system_notifications IS '시스템 알림';
COMMENT ON COLUMN notification_preference.quiet_hours_enabled IS '방해금지 모드';
COMMENT ON COLUMN notification_preference.quiet_hours_start IS '방해금지 시작 시간';
COMMENT ON COLUMN notification_preference.quiet_hours_end IS '방해금지 종료 시간';
```

---

### Phase 2: 코드 변경 (2일)

#### 4.2.1 DataSource Properties 생성

```java
// src/main/java/io/pinkspider/leveluptogethermvp/notificationservice/core/properties/NotificationDataSourceProperties.java
package io.pinkspider.leveluptogethermvp.notificationservice.core.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@ConfigurationProperties(prefix = "spring.datasource.notification")
@Configuration
@Getter
@Setter
public class NotificationDataSourceProperties {
    private String jdbcUrl;
    private String username;
    private String password;
    private String driverClassName;
}
```

#### 4.2.2 DataSource Config 생성

```java
// src/main/java/io/pinkspider/global/config/datasource/NotificationDataSourceConfig.java
package io.pinkspider.global.config.datasource;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.pinkspider.global.component.SshTunnel;
import io.pinkspider.leveluptogethermvp.notificationservice.core.properties.NotificationDataSourceProperties;
import jakarta.persistence.EntityManagerFactory;
import java.util.Properties;
import javax.sql.DataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@Configuration
@EnableTransactionManagement
@EnableJpaRepositories(
    basePackages = "io.pinkspider.leveluptogethermvp.notificationservice",
    entityManagerFactoryRef = "notificationEntityManagerFactory",
    transactionManagerRef = "notificationTransactionManager"
)
@Profile("!test")
@Slf4j
public class NotificationDataSourceConfig {

    private final NotificationDataSourceProperties properties;
    private final SshTunnel sshTunnel;

    public NotificationDataSourceConfig(NotificationDataSourceProperties properties, SshTunnel sshTunnel) {
        this.properties = properties;
        this.sshTunnel = sshTunnel;
    }

    @Bean(name = "notificationDataSource")
    @DependsOn("sshTunnel")
    public DataSource notificationDataSource() {
        HikariConfig cfg = new HikariConfig();

        String jdbcUrl = DataSourceUtils.replacePortInJdbcUrl(properties.getJdbcUrl(), sshTunnel.getActualLocalPort());
        log.info("Notification DataSource JDBC URL: {}", jdbcUrl);

        cfg.setJdbcUrl(jdbcUrl);
        cfg.setUsername(properties.getUsername());
        cfg.setPassword(properties.getPassword());
        cfg.setDriverClassName(properties.getDriverClassName());

        cfg.setInitializationFailTimeout(30000);
        cfg.setConnectionTimeout(15000);
        cfg.setValidationTimeout(5000);
        cfg.setMaximumPoolSize(5);  // 알림은 상대적으로 적은 풀
        cfg.setMinimumIdle(1);
        cfg.setMaxLifetime(1800000);
        cfg.setIdleTimeout(600000);
        cfg.setLeakDetectionThreshold(60000);

        return new HikariDataSource(cfg);
    }

    @Bean(name = "notificationEntityManagerFactory")
    public LocalContainerEntityManagerFactoryBean notificationEntityManagerFactory(
        @Qualifier("notificationDataSource") DataSource dataSource) {
        LocalContainerEntityManagerFactoryBean em = new LocalContainerEntityManagerFactoryBean();
        em.setDataSource(dataSource);
        em.setPackagesToScan("io.pinkspider.leveluptogethermvp.notificationservice");
        em.setPersistenceUnitName("notification");
        HibernateJpaVendorAdapter vendorAdapter = new HibernateJpaVendorAdapter();
        em.setJpaVendorAdapter(vendorAdapter);
        em.setJpaProperties(jpaProperties());
        return em;
    }

    @Bean(name = "notificationTransactionManager")
    public PlatformTransactionManager notificationTransactionManager(
        @Qualifier("notificationEntityManagerFactory") EntityManagerFactory entityManagerFactory) {
        return new JpaTransactionManager(entityManagerFactory);
    }

    private Properties jpaProperties() {
        Properties properties = new Properties();
        properties.setProperty("hibernate.hbm2ddl.auto", "validate");
        properties.setProperty("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect");
        properties.setProperty("hibernate.format_sql", "true");
        properties.setProperty("hibernate.show_sql", "false");
        return properties;
    }
}
```

#### 4.2.3 패키지 이동 및 트랜잭션 매니저 지정

```bash
# 패키지 이동 (IDE에서 Refactor > Move 사용)
io.pinkspider.leveluptogethermvp.userservice.notification
  → io.pinkspider.leveluptogethermvp.notificationservice
```

```java
// NotificationService.java 수정
@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true, transactionManager = "notificationTransactionManager")  // 변경
public class NotificationService {

    // ...

    @Transactional(transactionManager = "notificationTransactionManager")  // 모든 @Transactional에 추가
    public NotificationResponse createNotification(...) {
        // ...
    }

    // ... 모든 @Transactional 메서드에 transactionManager 지정
}
```

#### 4.2.4 Test 설정 추가

```java
// src/test/java/io/pinkspider/leveluptogethermvp/config/NotificationTestDataSourceConfig.java
package io.pinkspider.leveluptogethermvp.config;

import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.util.Properties;

@Configuration
@EnableJpaRepositories(
    basePackages = "io.pinkspider.leveluptogethermvp.notificationservice",
    entityManagerFactoryRef = "notificationEntityManagerFactory",
    transactionManagerRef = "notificationTransactionManager"
)
@Profile("test")
public class NotificationTestDataSourceConfig {

    @Bean(name = "notificationDataSource")
    public DataSource notificationDataSource() {
        return DataSourceBuilder.create()
            .driverClassName("org.h2.Driver")
            .url("jdbc:h2:mem:notification_db;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
            .username("sa")
            .password("")
            .build();
    }

    @Bean(name = "notificationEntityManagerFactory")
    public LocalContainerEntityManagerFactoryBean notificationEntityManagerFactory() {
        LocalContainerEntityManagerFactoryBean em = new LocalContainerEntityManagerFactoryBean();
        em.setDataSource(notificationDataSource());
        em.setPackagesToScan("io.pinkspider.leveluptogethermvp.notificationservice");
        em.setPersistenceUnitName("notification");

        HibernateJpaVendorAdapter vendorAdapter = new HibernateJpaVendorAdapter();
        em.setJpaVendorAdapter(vendorAdapter);

        Properties props = new Properties();
        props.setProperty("hibernate.hbm2ddl.auto", "create-drop");
        props.setProperty("hibernate.dialect", "org.hibernate.dialect.H2Dialect");
        em.setJpaProperties(props);

        return em;
    }

    @Bean(name = "notificationTransactionManager")
    public PlatformTransactionManager notificationTransactionManager() {
        return new JpaTransactionManager(notificationEntityManagerFactory().getObject());
    }
}
```

---

### Phase 3: 설정 파일 업데이트 (0.5일)

#### 4.3.1 Config Repository 업데이트

```yaml
# config-repository/level-up-together-mvp/application-dev.yml
spring:
  datasource:
    # ... 기존 설정 ...
    notification:  # 추가
      jdbc-url: jdbc:log4jdbc:postgresql://localhost:5433/notification_db?sslmode=disable&TimeZone=Asia/Seoul&characterEncoding=UTF-8
      username: "{cipher}cda1bb23f0a2832893e118b89372c257f7aaab31a10efe26bab29d062b309a79"
      password: "{cipher}4b8e2192e78b6bc99cfb94efa75bfc3fcf7522671c832c562ca6917482d7441478c7c79bd81e12b6747747d48a100c04"
      driver-class-name: net.sf.log4jdbc.sql.jdbcapi.DriverSpy
```

```yaml
# config-repository/level-up-together-mvp/application-local.yml (동일하게 추가)
```

---

### Phase 4: 데이터 마이그레이션 (0.5일)

#### 4.4.1 데이터 복사 (다운타임 최소화)

```sql
-- Step 1: 데이터 복사 (user_db에서 notification_db로)
-- pg_dump/pg_restore 또는 INSERT SELECT 사용

-- user_db에서 실행
\copy (SELECT * FROM notification) TO '/tmp/notification_data.csv' WITH CSV HEADER;
\copy (SELECT * FROM notification_preference) TO '/tmp/notification_preference_data.csv' WITH CSV HEADER;

-- notification_db에서 실행
\copy notification FROM '/tmp/notification_data.csv' WITH CSV HEADER;
\copy notification_preference FROM '/tmp/notification_preference_data.csv' WITH CSV HEADER;

-- Sequence 동기화
SELECT setval('notification_id_seq', (SELECT MAX(id) FROM notification));
SELECT setval('notification_preference_id_seq', (SELECT MAX(id) FROM notification_preference));
```

#### 4.4.2 검증

```sql
-- 데이터 건수 비교
-- user_db
SELECT COUNT(*) FROM notification;
SELECT COUNT(*) FROM notification_preference;

-- notification_db
SELECT COUNT(*) FROM notification;
SELECT COUNT(*) FROM notification_preference;
```

---

### Phase 5: 배포 및 전환 (0.5일)

#### 4.5.1 배포 순서

1. **DB 준비**: notification_db 생성 및 데이터 복사
2. **코드 배포**: 새 DataSource 설정이 포함된 버전 배포
3. **검증**: API 테스트
4. **user_db 테이블 제거** (1주 후, 안정화 확인 후)

#### 4.5.2 롤백 계획

```yaml
# 롤백 시: application-dev.yml에서 notification DataSource 제거
# 코드: 이전 버전으로 롤백 (패키지 경로 원복)
```

---

### Phase 6: 정리 (1주 후)

#### 4.6.1 user_db에서 테이블 제거

```sql
-- 신규 DB 안정화 확인 후 실행 (1주 후)
-- user_db에서 실행

-- 백업 먼저
CREATE TABLE notification_backup AS SELECT * FROM notification;
CREATE TABLE notification_preference_backup AS SELECT * FROM notification_preference;

-- 테이블 삭제
DROP TABLE notification;
DROP TABLE notification_preference;
```

---

## 5. 체크리스트

### 5.1 마이그레이션 전

- [ ] notification_db 데이터베이스 생성
- [ ] 테이블 스키마 생성
- [ ] 인덱스 생성
- [ ] DB 사용자 권한 부여

### 5.2 코드 변경

- [ ] NotificationDataSourceProperties.java 생성
- [ ] NotificationDataSourceConfig.java 생성
- [ ] 패키지 이동: userservice.notification → notificationservice
- [ ] @Transactional에 transactionManager 지정
- [ ] 테스트 설정 추가
- [ ] 단위 테스트 통과 확인

### 5.3 설정 파일

- [ ] application-dev.yml 업데이트
- [ ] application-local.yml 업데이트
- [ ] application-prod.yml 업데이트

### 5.4 데이터 마이그레이션

- [ ] 기존 데이터 백업
- [ ] notification_db로 데이터 복사
- [ ] 데이터 검증 (건수 비교)
- [ ] Sequence 동기화

### 5.5 배포 후

- [ ] API 테스트 (/api/v1/notifications)
- [ ] 알림 생성 테스트
- [ ] 알림 읽음 처리 테스트
- [ ] 알림 설정 변경 테스트
- [ ] 모니터링 (에러 로그)

### 5.6 정리 (1주 후)

- [ ] user_db 테이블 백업
- [ ] user_db 테이블 삭제

---

## 6. 예상 일정

| Phase | 작업 | 소요 시간 |
|---|---|---|
| Phase 1 | 인프라 준비 | 1일 |
| Phase 2 | 코드 변경 | 2일 |
| Phase 3 | 설정 파일 업데이트 | 0.5일 |
| Phase 4 | 데이터 마이그레이션 | 0.5일 |
| Phase 5 | 배포 및 전환 | 0.5일 |
| Phase 6 | 정리 | 1주 후 |
| **총합** | | **4.5일 + 모니터링** |

---

## 7. 리스크 및 대응

| 리스크 | 영향 | 대응 |
|---|---|---|
| 트랜잭션 매니저 누락 | 데이터 저장 실패 | @Transactional에 명시적 지정, 테스트 커버리지 |
| 데이터 유실 | 알림 손실 | 마이그레이션 전 백업, 롤백 계획 |
| 연결 풀 부족 | 성능 저하 | 모니터링, 필요시 풀 사이즈 조정 |
| 배포 실패 | 서비스 중단 | 롤백 스크립트 준비 |

---

## 8. 참고 사항

### 8.1 다른 서비스에서 NotificationService 호출 시

NotificationService를 호출하는 다른 서비스들은 변경 없이 동작합니다.
패키지 경로만 변경되므로 import문만 업데이트하면 됩니다.

```java
// 변경 전
import io.pinkspider.leveluptogethermvp.userservice.notification.application.NotificationService;

// 변경 후
import io.pinkspider.leveluptogethermvp.notificationservice.application.NotificationService;
```

### 8.2 CLAUDE.md 업데이트

마이그레이션 완료 후 CLAUDE.md의 트랜잭션 매니저 매핑 테이블에 추가:

```markdown
| notificationservice | `notificationTransactionManager` |
```
