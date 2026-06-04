# 코드 포맷팅 가이드 (Spotless + Palantir Java Format)

본 저장소(Java/Gradle)는 **Spotless + palantir-java-format**으로 자동 포맷팅을 강제합니다. CI에서 `spotlessCheck`가 실패하면 빌드가 깨지고 PR 머지가 불가능하므로 IDE 설정을 반드시 맞춰주세요.

> Frontend 저장소(`level-up-together-frontend`, `level-up-together-admin-frontend`)는 Prettier를 사용합니다. 각 저장소의 `docs/CODE_FORMATTING.md` 참조.

## 적용 정보

| 항목 | 값 |
|---|---|
| 도구 | Spotless 6.25 + palantir-java-format 2.50 |
| 스타일 | Palantir Java Style (4-space, 120 column) |
| 미사용 import 제거 | ✓ |
| trailing whitespace 제거 | ✓ |
| end-with-newline 강제 | ✓ |
| 제외 경로 | `**/build/**`, `**/build/generated/**` (QueryDSL Q 클래스 포함) |

## 명령

```bash
./gradlew spotlessApply   # 자동 포맷 적용
./gradlew spotlessCheck   # 검사만 (CI에서 사용 — 위반 시 빌드 실패)

# 단일 파일만
./gradlew :service:spotlessJavaApply -PspotlessFiles=$(realpath path/to/File.java)
```

## IntelliJ IDEA 설정 (필수)

IntelliJ on-save 포맷터가 Palantir와 **같은 결과**를 내도록 맞춰주세요. 안 하면 매 저장마다 포맷이 흔들립니다(ping-pong).

### 1. Palantir Java Format 플러그인 설치
- `Settings → Plugins → Marketplace`에서 `palantir-java-format` 검색 후 설치
- IntelliJ 재시작

### 2. 플러그인 활성화
- `Settings → Other Settings → palantir-java-format Settings`
- `Enable palantir-java-format` ✓
- `Default formatter` 로 지정

### 3. JDK 17+ 내부 API 접근 허용
IntelliJ가 자체 임베디드 JRE를 쓰므로 `--add-exports` 옵션이 필요합니다.

- `Help → Edit Custom VM Options` 열기
- 파일 끝에 다음 라인 추가:
  ```
  --add-exports jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED
  --add-exports jdk.compiler/com.sun.tools.javac.code=ALL-UNNAMED
  --add-exports jdk.compiler/com.sun.tools.javac.file=ALL-UNNAMED
  --add-exports jdk.compiler/com.sun.tools.javac.parser=ALL-UNNAMED
  --add-exports jdk.compiler/com.sun.tools.javac.tree=ALL-UNNAMED
  --add-exports jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED
  ```
- IntelliJ 재시작

### 4. 저장 시 자동 정리 (권장)
- `Settings → Tools → Actions on Save`
- `Reformat code` ✓
- `Optimize imports` ✓

### 5. 확인
- 아무 Java 파일을 열고 `Cmd+Opt+L`(Mac) / `Ctrl+Alt+L`(Win) 후 `./gradlew spotlessCheck` 통과하면 OK

## VSCode / Cursor 사용자
Java 작업에 VSCode/Cursor를 쓰는 경우, "Language Support for Java by Red Hat" 확장이 기본 포매터를 잡아갑니다. Palantir와 다른 결과를 내므로 다음 중 하나로 우회:
- 저장 시 포맷 OFF: `"editor.formatOnSave": false` (Java 파일에 한정)
- 또는 `./gradlew spotlessApply`를 수동으로 돌린 뒤 커밋

## 트러블슈팅

### IntelliJ에서 저장할 때마다 포맷이 변함 / `spotlessCheck`와 다른 결과
→ Palantir 플러그인 설치 + Default formatter 지정이 안 됨. 위 1~3 단계 재확인.

### `IllegalAccessError: ... com.sun.tools.javac.parser ...`
→ Custom VM Options에 `--add-exports` 라인 누락. 위 3번 재확인.

### CI에서 `spotlessCheck FAILED`
→ 로컬에서 `./gradlew spotlessApply` 돌리고 결과를 커밋. PR 다시 push.

### Spotless가 generated 파일(QueryDSL Q 클래스)을 만지려고 함
→ `build.gradle`의 `targetExclude '**/build/**'`로 이미 제외됨. 새 생성 디렉토리가 생기면 같이 추가.

### 메서드 체인 들여쓰기가 8 spaces로 보임
→ Palantir의 의도된 동작. 메서드 체인은 한 단계 더 들여쓰기 (4 → 8). 일반 들여쓰기는 그대로 4 spaces.

## 왜 Palantir 인가?
- google-java-format(2-space)은 기존 4-space 코드 베이스와 충돌이 너무 큼
- Palantir는 4-space 유지 + 합리적 wrapping
- spotless가 forked JVM에서 실행하므로 JVM args 추가 필요 없음 (IntelliJ만 별도 설정)

## 변경 이력
- 2026-06-04 도입. 첫 일괄 reformat.
