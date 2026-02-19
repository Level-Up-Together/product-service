#!/bin/bash
LOG_DIR="/Users/pink-spider/Code/github/Level-Up-Together/product-service/prompt_log"
DATE=$(date +%F)
TIME=$(date +%T)

mkdir -p "$LOG_DIR"

# stdin으로 전달되는 JSON 읽기
INPUT=$(cat)

# 이벤트 타입과 사용자 프롬프트 추출
HOOK_EVENT=$(echo "$INPUT" | jq -r '.hook_event_name // empty' 2>/dev/null)
PROMPT=$(echo "$INPUT" | jq -r '.prompt // empty' 2>/dev/null)
TRANSCRIPT_PATH=$(echo "$INPUT" | jq -r '.transcript_path // empty' 2>/dev/null)
SESSION_ID=$(echo "$INPUT" | jq -r '.session_id // empty' 2>/dev/null)

# raw 데이터 기록
echo "=== $TIME $HOOK_EVENT ===" >> "$LOG_DIR/claude_raw_$DATE.log"
echo "$INPUT" >> "$LOG_DIR/claude_raw_$DATE.log"

# UserPromptSubmit 이벤트: 사용자 입력 로깅
if [ "$HOOK_EVENT" = "UserPromptSubmit" ] && [ -n "$PROMPT" ]; then
    echo -e "\n[$TIME] USER: $PROMPT" >> "$LOG_DIR/claude_log_$DATE.log"
fi

# Stop 이벤트: transcript 파일을 세션별로 복사 + 마지막 응답 기록
if [ "$HOOK_EVENT" = "Stop" ] && [ -n "$TRANSCRIPT_PATH" ] && [ -f "$TRANSCRIPT_PATH" ]; then
    # 세션별 transcript 복사 (전체 대화 보존)
    if [ -n "$SESSION_ID" ]; then
        cp "$TRANSCRIPT_PATH" "$LOG_DIR/transcript_${SESSION_ID}.jsonl"
    else
        # session_id가 없으면 파일명에서 추출
        BASENAME=$(basename "$TRANSCRIPT_PATH")
        cp "$TRANSCRIPT_PATH" "$LOG_DIR/$BASENAME"
    fi

    # 마지막 assistant 응답만 로그에 추가 (중복 방지)
    RESPONSE=$(jq -rs '[.[] | select(.type == "assistant") | .message.content[]? | select(.type == "text") | .text] | last // empty' "$TRANSCRIPT_PATH" 2>/dev/null)

    if [ -n "$RESPONSE" ]; then
        echo -e "[$TIME] CLAUDE: $RESPONSE" >> "$LOG_DIR/claude_log_$DATE.log"
    fi
fi
