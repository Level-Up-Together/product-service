#!/bin/bash
LOG_DIR="/Users/pink-spider/Code/github/Level-Up-Together/level-up-together-mvp/prompt_log"
DATE=$(date +%F)
TIME=$(date +%T)

# 로그 디렉토리 생성 (없으면)
mkdir -p "$LOG_DIR"

# stdin을 변수로 읽기
INPUT=$(cat)

# raw 데이터 기록
echo "=== $TIME ===" >> "$LOG_DIR/claude_raw_$DATE.log"
echo "$INPUT" >> "$LOG_DIR/claude_raw_$DATE.log"

# prompt 추출 시도
PROMPT=$(echo "$INPUT" | jq -r '.prompt // empty' 2>/dev/null)

if [ -n "$PROMPT" ]; then
    echo -e "\n[$TIME] USER: $PROMPT" >> "$LOG_DIR/claude_log_$DATE.log"
else
    echo -e "\n[$TIME] USER: (no prompt in input)" >> "$LOG_DIR/claude_log_$DATE.log"
fi