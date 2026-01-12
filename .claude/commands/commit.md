---
description: 변경사항 분석 후 커밋 메시지 생성
allowed-tools: Bash(git status:*), Bash(git diff:*), Bash(git log:*)
model: Sonnet 4.5
---

작업 내용을 분석하고 적절한 커밋 메시지를 생성해줘.
여러 프로젝트를 작업했다면 프로젝트별로 커밋 메세지를 만들어야 한다.

규칙:

- 형식: `type: 설명` (feat, fix, refactor, docs, test, chore)
- 한글로 작성
- 50자 이내
- with claude 필요 없음

변경된 파일들을 확인하고 커밋 메시지를 제안해줘.
커밋은 직접 한다.