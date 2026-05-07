#!/usr/bin/env bash
# /opt/apps/deploy.sh
# prod EC2 에서 GitHub Actions(SSM) 가 호출하는 배포 스크립트.
#
# 인자: APP_NAME S3_BUCKET S3_KEY JAR_FILENAME
# 예시:
#   sudo /opt/apps/deploy.sh \
#     product-service \
#     lut-deploy-prod \
#     product-service/latest.jar \
#     product-service-0.0.2-SNAPSHOT-exec.jar
#
# 동작:
#   1) 이전 JAR 삭제 (구버전 실행 방지)
#   2) S3 에서 새 JAR 다운로드 (--quiet 로 SSM 24KB 출력 한계 회피)
#   3) systemd 서비스 파일 생성/갱신
#   4) systemctl restart + 부팅 대기 후 상태 검증
#
# 영구화: 신규 EC2 부팅 시 user-data.sh 에서 본 파일을 S3 또는 git raw 로
#         /opt/apps/deploy.sh 로 다운로드하도록 보강 권장.

set -euo pipefail

APP_NAME="$1"
S3_BUCKET="$2"
S3_KEY="$3"
JAR_FILENAME="$4"

APP_DIR="/opt/apps/${APP_NAME}"
JAR_PATH="${APP_DIR}/${JAR_FILENAME}"
SERVICE_FILE="/etc/systemd/system/${APP_NAME}.service"
LOG_FILE="/var/log/${APP_NAME}.log"

mkdir -p "$APP_DIR"
# Logback 등 상대경로 ./logs/ 사용 대비 — WorkingDirectory 기준 logs 디렉토리 보장
mkdir -p "${APP_DIR}/logs"
# 서비스가 ec2-user 로 실행되므로 디렉토리 소유권 위임 (logs 파일 생성 가능)
chown -R ec2-user:ec2-user "$APP_DIR"

# 1) 이전 JAR 삭제 (구버전 실행 방지)
find "$APP_DIR" -maxdepth 1 -name '*.jar' ! -name "${JAR_FILENAME}" -delete 2>/dev/null || true

# 2) S3 에서 새 JAR 다운로드 (--quiet 로 SSM 24KB 출력 한계 회피)
aws s3 cp --quiet "s3://${S3_BUCKET}/${S3_KEY}" "$JAR_PATH"
chmod 644 "$JAR_PATH"

# 3) systemd 서비스 파일 생성/갱신
cat > "$SERVICE_FILE" <<UNIT
[Unit]
Description=${APP_NAME}
After=network.target

[Service]
User=ec2-user
WorkingDirectory=${APP_DIR}
ExecStart=/usr/bin/java -jar ${JAR_PATH} --spring.profiles.active=prod
SuccessExitStatus=143
Restart=on-failure
RestartSec=10
StandardOutput=append:${LOG_FILE}
StandardError=append:${LOG_FILE}

[Install]
WantedBy=multi-user.target
UNIT

systemctl daemon-reload
systemctl enable "${APP_NAME}"
systemctl restart "${APP_NAME}"

# 4) Spring Boot 부팅 대기 + 상태 검증
sleep 45
systemctl is-active "${APP_NAME}"
