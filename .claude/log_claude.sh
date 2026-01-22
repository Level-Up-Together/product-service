#!/bin/bash
LOG_DIR="/Users/pink-spider/Code/github/Level-Up-Together/level-up-together-mvp/prompt_log"
DATE=$(date +%F)
TIME=$(date +%T)

mkdir -p "$LOG_DIR"

# stdin으로 전달되는 JSON 읽기
INPUT=$(cat)

# 이벤트 타입과 사용자 프롬프트 추출
HOOK_EVENT=$(echo "$INPUT" | jq -r '.hook_event_name // empty' 2>/dev/null)
PROMPT=$(echo "$INPUT" | jq -r '.prompt // empty' 2>/dev/null)
TRANSCRIPT_PATH=$(echo "$INPUT" | jq -r '.transcript_path // empty' 2>/dev/null)

# raw 데이터 기록
echo "=== $TIME $HOOK_EVENT ===" >> "$LOG_DIR/claude_raw_$DATE.log"
echo "$INPUT" >> "$LOG_DIR/claude_raw_$DATE.log"

# UserPromptSubmit 이벤트: 사용자 입력 로깅
if [ "$HOOK_EVENT" = "UserPromptSubmit" ] && [ -n "$PROMPT" ]; then
    echo -e "\n[$TIME] USER: $PROMPT" >> "$LOG_DIR/claude_log_$DATE.log"
fi

# Stop 이벤트: Claude 응답 로깅
if [ "$HOOK_EVENT" = "Stop" ] && [ -n "$TRANSCRIPT_PATH" ] && [ -f "$TRANSCRIPT_PATH" ]; then
    # JSONL 파일에서 마지막 text 타입 assistant 메시지 추출 (-s로 slurp)
    RESPONSE=$(jq -rs '[.[] | select(.type == "assistant") | .message.content[]? | select(.type == "text") | .text] | last // empty' "$TRANSCRIPT_PATH" 2>/dev/null)

    if [ -n "$RESPONSE" ]; then
        # 전체 응답 기록 (제한 없음)
        echo -e "[$TIME] CLAUDE: $RESPONSE" >> "$LOG_DIR/claude_log_$DATE.log"
    fi
fi
