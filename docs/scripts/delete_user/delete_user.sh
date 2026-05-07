#!/usr/bin/env bash
#
# delete_user.sh - 특정 사용자의 모든 데이터를 8개 DB에서 삭제
#
# 사용법:
#   PGPASSWORD=<password> ./delete_user.sh <user-uuid> [--dry-run] [--yes]
#
# 옵션:
#   --dry-run   실제 삭제 없이 영향 row 수만 출력 (count SQL 실행)
#   --yes       확인 프롬프트 스킵 (자동화용)
#
# 환경변수:
#   PGPASSWORD       (필수) DB 비밀번호
#   DB_HOST          DB 호스트 (기본: localhost)
#                    - dev: 온프레미스 PostgreSQL
#                    - prod: AWS RDS 엔드포인트 (보통 SSH 터널 또는 EC2 안에서 실행)
#   DB_PORT          포트 (기본: 5432)
#   DB_USER          DB 사용자 (기본: lut)
#   DBS              처리할 DB 목록 (기본: 8개 전체)
#
# 예시:
#   # dev (온프레미스): 사전 검증
#   PGPASSWORD=xxx DB_HOST=dev-db.internal \
#     ./delete_user.sh abc-123-uuid --dry-run
#
#   # dev: 실제 삭제
#   PGPASSWORD=xxx DB_HOST=dev-db.internal \
#     ./delete_user.sh abc-123-uuid
#
#   # prod (RDS): SSH 터널 후 실행
#   ssh -f -N -L 15432:<prod-rds>:5432 -i ~/.ssh/<key>.pem ec2-user@<EC2_IP>
#   PGPASSWORD=xxx DB_HOST=localhost DB_PORT=15432 \
#     ./delete_user.sh abc-123-uuid

set -euo pipefail

# ---- 인자 파싱 -------------------------------------------------------------
USER_ID="${1:-}"
DRY_RUN=false
SKIP_CONFIRM=false

shift || true
for arg in "$@"; do
  case "$arg" in
    --dry-run) DRY_RUN=true ;;
    --yes|-y)  SKIP_CONFIRM=true ;;
    *) echo "Unknown option: $arg" >&2; exit 1 ;;
  esac
done

if [[ -z "$USER_ID" ]]; then
  echo "Usage: $0 <user-uuid> [--dry-run] [--yes]" >&2
  exit 1
fi

# ---- 환경변수 기본값 -------------------------------------------------------
: "${PGPASSWORD:?PGPASSWORD is required}"
# RDS_* 도 backward-compatible alias로 허용 (이전 사용 코드 호환)
DB_HOST="${DB_HOST:-${RDS_HOST:-localhost}}"
DB_PORT="${DB_PORT:-${RDS_PORT:-5432}}"
DB_USER="${DB_USER:-${RDS_USER:-lut}}"
DBS="${DBS:-user_db mission_db guild_db chat_db feed_db notification_db gamification_db admin_db}"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SQL_DIR="${SCRIPT_DIR}/sql"

# ---- 헬퍼 ------------------------------------------------------------------
psql_run() {
  local db="$1"; shift
  # NOTE: -v uid는 quote 없이 raw 값으로 전달.
  # SQL 파일에서 :'uid' 문법이 자동으로 single quote로 감싸므로 (psql 9.0+)
  # 여기서 quote를 추가하면 이중 quote가 되어 매칭 실패.
  PGPASSWORD="$PGPASSWORD" psql \
    -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$db" \
    -v ON_ERROR_STOP=1 -v uid="$USER_ID" \
    --pset=pager=off \
    "$@"
}

# ---- dry-run: count만 실행 -------------------------------------------------
if $DRY_RUN; then
  echo "=== DRY RUN: counting rows for user $USER_ID ==="
  for DB in $DBS; do
    psql_run "$DB" -f "${SQL_DIR}/count/${DB}.sql" || {
      echo "[ERR] count failed on $DB" >&2; exit 1; }
  done
  echo "=== DRY RUN: done ==="
  exit 0
fi

# ---- 확인 프롬프트 ---------------------------------------------------------
echo "About to DELETE all data for user: $USER_ID"
echo "  Host : $DB_HOST:$DB_PORT"
echo "  User : $DB_USER"
echo "  DBs  : $DBS"
echo

if ! $SKIP_CONFIRM; then
  read -r -p 'Type "DELETE" to continue: ' answer
  if [[ "$answer" != "DELETE" ]]; then
    echo "Aborted."
    exit 1
  fi
fi

# ---- 삭제 실행 -------------------------------------------------------------
for DB in $DBS; do
  psql_run "$DB" -f "${SQL_DIR}/delete/${DB}.sql" || {
    echo "[ERR] delete failed on $DB — 트랜잭션 자동 롤백됨" >&2
    exit 1
  }
done

echo
echo "=== DONE: $USER_ID 데이터 삭제 완료 ==="
echo "[INFO] Redis 캐시 별도 정리 권장 — README 참고"
