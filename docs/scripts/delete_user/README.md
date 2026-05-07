# 사용자 데이터 일괄 삭제 스크립트

특정 사용자의 데이터를 9개 데이터베이스 중 영향이 있는 8개 DB에서 일괄 삭제합니다.

> ⚠️ **주의**: 모든 옵션은 destructive 작업입니다. dev에서 검증 후 prod에 적용하고, 실행 전 `--dry-run`으로 영향 범위를 반드시 확인하세요.

## 디렉토리 구조

```
docs/scripts/delete_user/
├── README.md
├── delete_user.sh                # 통합 실행 스크립트
└── sql/
    ├── delete/                   # 실제 삭제용 (BEGIN/COMMIT 포함)
    │   ├── user_db.sql
    │   ├── mission_db.sql
    │   ├── guild_db.sql
    │   ├── chat_db.sql
    │   ├── feed_db.sql
    │   ├── notification_db.sql
    │   ├── gamification_db.sql
    │   └── admin_db.sql
    └── count/                    # 사전 검증용 (영향 row 수 출력)
        ├── user_db.sql
        ├── mission_db.sql
        ├── ...
        └── admin_db.sql
```

## 영향 범위 (DB별 테이블)

| DB | 테이블 | 매칭 컬럼 |
|---|---|---|
| **user_db** | `users`, `friendship`, `user_term_agreements`, `user_blacklist`, `daily_mvp_exclusion` | `id`, `user_id` + **`friend_id`(양방향)** |
| **mission_db** | `mission`, `mission_template`, `mission_participant`, `mission_comment` | `creator_id`, `user_id` |
| **guild_db** | `guild_member`, `guild_invitation`, `guild_post`, `guild_post_comment`, `guild_join_request`, `guild_experience_history` | `user_id`, `inviter_id/invitee_id`, `author_id`, `requester_id/processed_by`, `contributor_id` |
| **chat_db** | `guild_chat_participant`, `guild_chat_read_status`, `guild_chat_message`, `guild_direct_conversation`, `guild_direct_message` | `user_id`, `sender_id`, `user_id_1/2` |
| **feed_db** | `activity_feed`, `feed_comment`, `feed_like` | `user_id` |
| **notification_db** | `notification`, `notification_preference`, `device_token` | `user_id` |
| **gamification_db** | `user_experience`, `user_stats`, `user_title`, `user_achievement`, `user_category_experience`, `experience_history`, `attendance_record`, `season_reward_history`, `daily_mvp_history`, `daily_mvp_category_stats` | `user_id` |
| **admin_db** | `customer_inquiry`, `featured_player`, (선택) `content_report` | `user_id`, `reporter_id`, `target_user_id` |
| meta_db / saga_db | (해당 없음) | - |

### 정책 결정이 필요한 항목 (기본 미삭제)

| 항목 | 위치 | 기본 동작 |
|---|---|---|
| **길드 마스터인 길드** | `guild_db.guild.master_id` | 미삭제 — `delete/guild_db.sql`이 사전에 해당 길드 목록 출력 → 마스터 위임/통째 삭제/보존 중 수동 결정 |
| **신고 이력** | `admin_db.content_report` (reporter_id, target_user_id) | 미삭제 — 감사용 보존 권장. 필요 시 `delete/admin_db.sql`의 주석 해제 |

## 환경별 DB 위치

| 환경 | DB 종류 | 접속 방법 |
|---|---|---|
| **dev** | **온프레미스 PostgreSQL** | 사내망에서 직접 접근 (또는 VPN) |
| **prod** | AWS RDS PostgreSQL | EC2(`lut-ec2-1` 또는 `lut-ec2-2`) 안에서 / SSH 터널 / SSM |

스크립트의 환경변수 `DB_HOST` 한 개로 양쪽 모두 대응합니다 (`RDS_HOST`도 backward-compatible alias로 허용).

## 사용법

### 1. dev (온프레미스 DB)

사내망에서 직접 접근하거나 VPN 연결 후:

```bash
# 1) 사전 검증 (dry-run)
PGPASSWORD='<dev-password>' \
DB_HOST='<dev-db-host>' DB_USER='lut' \
  ./delete_user.sh abc-123-uuid --dry-run

# 2) 실제 삭제 (DELETE 프롬프트 확인)
PGPASSWORD='<dev-password>' DB_HOST='<dev-db-host>' \
  ./delete_user.sh abc-123-uuid

# 3) 자동화 — 프롬프트 스킵
PGPASSWORD='<dev-password>' DB_HOST='<dev-db-host>' \
  ./delete_user.sh abc-123-uuid --yes
```

dry-run 출력 예시:
```
=== user_db: counting rows for 'abc-123-uuid' ===
        tbl          | cnt
---------------------+-----
 users               |   1
 friendship          |   3
 user_blacklist      |   0
 user_term_agreements|   2
 daily_mvp_exclusion |   0
=== mission_db: counting rows for 'abc-123-uuid' ===
...
```

### 2. prod (AWS RDS)

RDS는 사설망에 있으므로 SSH 터널 또는 EC2 안에서 실행:

**옵션 A: SSH 터널 (로컬에서 실행)**
```bash
# 터널 백그라운드 실행 (포트 15432로 매핑)
ssh -f -N -L 15432:<PROD_RDS_ENDPOINT>:5432 -i ~/.ssh/<key>.pem ec2-user@43.200.33.125

# 스크립트 실행
PGPASSWORD='<prod-password>' DB_HOST='localhost' DB_PORT=15432 \
  ./delete_user.sh abc-123-uuid --dry-run

# 종료
pkill -f "ssh.*15432:.*:5432"
```

**옵션 B: SSM Session Manager (EC2 안에서 직접 실행)**
```bash
aws ssm start-session --target i-0687f947dc8cb021a --region ap-northeast-2

# EC2 안에서:
PGPASSWORD='...' DB_HOST='<prod-rds>' /opt/scripts/delete_user/delete_user.sh <uuid>
```

**옵션 C: SSM Send Command (자동화)**
```bash
# 사전: 스크립트를 EC2에 배포 (1회)
aws s3 cp docs/scripts/delete_user s3://lut-deploy-prod/scripts/delete_user --recursive
aws ssm send-command \
  --instance-ids i-0687f947dc8cb021a \
  --document-name AWS-RunShellScript \
  --parameters "commands=[\"sudo aws s3 sync s3://lut-deploy-prod/scripts/delete_user /opt/scripts/delete_user && sudo chmod +x /opt/scripts/delete_user/delete_user.sh\"]"

# 트리거
USER_ID='abc-123-uuid'
aws ssm send-command \
  --instance-ids i-0687f947dc8cb021a \
  --document-name AWS-RunShellScript \
  --comment "delete user $USER_ID" \
  --parameters "commands=[\"PGPASSWORD='<password>' DB_HOST='<prod-rds>' /opt/scripts/delete_user/delete_user.sh '$USER_ID' --yes\"]" \
  --query "Command.CommandId" --output text
```

### 3. 일부 DB만 처리

`DBS` 환경변수로 대상 DB 한정 (공백 구분):

```bash
DBS='feed_db notification_db' PGPASSWORD=... DB_HOST=... ./delete_user.sh <uuid>
```

## 안전 권고

1. **항상 `--dry-run` 먼저** — 영향 row 수 확인
2. **트랜잭션 자동 롤백** — 각 DB의 SQL은 `BEGIN; ... COMMIT;`으로 감싸져 있어 중간 실패 시 자동 롤백
3. **`ON_ERROR_STOP=1`** — `psql`이 한 SQL 실패 시 즉시 중단, 다음 DB로 넘어가지 않음
4. **dev에서 먼저 검증** — prod 적용 전 dev에서 1회 dry-run + 실제 삭제 후 정합성 확인
5. **백업** — 운영 환경(prod RDS)은 사전 `pg_dump` 권장
   ```bash
   pg_dump -h $DB_HOST -U $DB_USER -d user_db -t users -t friendship \
     -F custom -f backup_user_$(date +%Y%m%d).dump
   ```
6. **길드 마스터 처리** — `guild_db` SQL이 사전에 마스터 길드 목록을 출력하므로 그 결과를 보고 정책 결정
7. **Redis 캐시 정리** — DB 삭제 후 Redis 키도 함께 정리:
   ```bash
   USER_ID='abc-123-uuid'
   redis-cli --scan --pattern "userProfile:$USER_ID"        | xargs -r redis-cli del
   redis-cli --scan --pattern "friendIds:$USER_ID"          | xargs -r redis-cli del
   redis-cli --scan --pattern "userTitleInfo:$USER_ID"      | xargs -r redis-cli del
   redis-cli --scan --pattern "userSessions:$USER_ID"       | xargs -r redis-cli del
   # OAuth 세션 (session:* 는 hash, 직접 매칭 어려움 — TTL 만료 대기 또는 별도 정리)
   ```

## 사용 시나리오

| 시나리오 | 명령 |
|---|---|
| QA 테스트 계정 정리 (dev) | `--dry-run` → 결과 확인 → 실제 실행 |
| 회원 탈퇴 처리 (수동, 임시) | App에서 회원 탈퇴 API 미구현 시 임시 운영 도구로 사용 |
| GDPR/개인정보 삭제 요청 | 백업 후 실제 삭제, 신고 이력은 보존 (`content_report` 미삭제 기본 동작) |
| 잘못 생성된 테스트 계정 | `--yes` 자동 모드로 일괄 처리 |

## SQL 직접 실행 (스크립트 없이)

```bash
# 단일 DB만 검증 (dev 온프레미스 / prod RDS 동일)
PGPASSWORD=... psql -h <db-host> -U lut -d feed_db \
  -v ON_ERROR_STOP=1 -v uid="'abc-123-uuid'" \
  -f sql/count/feed_db.sql

# 단일 DB 삭제
PGPASSWORD=... psql -h <db-host> -U lut -d feed_db \
  -v ON_ERROR_STOP=1 -v uid="'abc-123-uuid'" \
  -f sql/delete/feed_db.sql
```

## 향후 개선 아이디어

- [ ] saga_db `saga_instance.payload` JSON에 user_id가 들어있을 가능성 — 별도 정합성 점검
- [ ] `content_report` 처리 정책 (마스킹 vs 삭제) 결정 후 옵션 추가
- [ ] Redis 키 정리를 스크립트에 통합 (`--cleanup-cache` 옵션)
- [ ] 삭제 이력을 audit log 테이블에 기록 (감사용)
