# 코드 포맷팅 가이드 (Spotless + google-java-format AOSP)

본 저장소(Java/Gradle)는 **Spotless + google-java-format (AOSP style)**으로 자동 포맷팅을 강제합니다. CI에서 `spotlessCheck`가 실패하면 빌드가 깨지고 PR 머지가 불가능합니다.

> Frontend 저장소(`level-up-together-frontend`, `level-up-together-admin-frontend`)는 Prettier를 사용합니다. 각 저장소의 `docs/CODE_FORMATTING.md` 참조.

## 적용 정보

| 항목 | 값 |
|---|---|
| 도구 | Spotless 6.25 + google-java-format 1.22 (AOSP) |
| 스타일 | AOSP Java Style (4-space, 100 column) |
| Import 정렬 | ✓ alphabet + group 분리 자동 |
| 미사용 import 제거 | ✓ |
| trailing whitespace 제거 | ✓ |
| end-with-newline 강제 | ✓ |
| 제외 경로 | `**/build/**`, `**/build/generated/**` (QueryDSL Q 클래스 포함) |

## 명령

```bash
./gradlew spotlessApply   # 자동 포맷 적용
./gradlew spotlessCheck   # 검사만 (CI에서 사용 — 위반 시 빌드 실패)

# 단일 모듈
./gradlew :service:spotlessApply
```

## IntelliJ IDEA 설정 (권장)

google-java-format은 IntelliJ Default Code Style이 만드는 결과와 거의 동일합니다. 일반적으로 별도 플러그인 없이도 잘 작동하지만, 완벽한 일치를 위해 플러그인 설치를 권장합니다.

### 옵션 A. google-java-format 플러그인 설치 (권장)

1. **플러그인 설치**
   - `Settings → Plugins → Marketplace`에서 `google-java-format` 검색 후 설치
   - IntelliJ 재시작

2. **플러그인 활성화**
   - `Settings → Other Settings → google-java-format Settings`
   - `Enable google-java-format` ✓
   - **Code Style: `AOSP`** 선택

3. **JDK 17+ 내부 API 접근 허용**
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

4. **저장 시 자동 정리 (권장)**
   - `Settings → Tools → Actions on Save`
   - `Reformat code` ✓
   - `Optimize imports` ✓ (google-java-format AOSP가 import 정렬도 자동 처리)

5. **이전에 설치한 palantir-java-format 플러그인은 비활성화**
   - `Settings → Plugins → Installed`에서 `palantir-java-format` Disable
   - 동시에 두 개 활성화 시 충돌 가능

### 옵션 B. 플러그인 없이 IntelliJ 내장 포매터 사용

google-java-format AOSP는 IntelliJ Default Code Style과 거의 동일합니다. 다음 설정으로 충분:
- `Settings → Editor → Code Style → Scheme: Default IDE`
- `Settings → Tools → Actions on Save`
  - `Reformat code` ✓
  - `Optimize imports` ✓

미세한 차이(메서드 체인 wrap 등)가 발생할 수 있지만, 그건 매번 spotlessApply로 정정 가능합니다.

### 6. 확인

- 아무 Java 파일을 열고 `Cmd+Opt+L`(Mac) / `Ctrl+Alt+L`(Win) → 저장 → `./gradlew spotlessCheck` 통과하면 OK

## VSCode / Cursor 사용자

Java 작업에 VSCode/Cursor를 쓰는 경우, "Language Support for Java by Red Hat" 확장이 기본 포매터를 잡아갑니다:
- `"java.format.settings.url"`에 google-java-format AOSP 설정 XML을 지정하거나
- 저장 시 포맷 OFF + 수동 `./gradlew spotlessApply` 후 커밋

## Claude 작업 패턴

Claude가 본 저장소 Java 코드를 작성/수정하면 마지막에 반드시 다음을 실행합니다:

```bash
./gradlew :모듈명:spotlessApply
```

이로써 작성된 코드는 항상 AOSP 룰에 맞는 형식으로 디스크에 기록됩니다. PR 시점에 추가 reformat 커밋이 발생하지 않습니다.

## 트러블슈팅

### IntelliJ에서 저장할 때마다 포맷이 변함 / `spotlessCheck`와 다른 결과
→ google-java-format 플러그인 미설치 또는 Code Style이 Default IDE가 아님. 위 IDE 설정 재확인.

### `IllegalAccessError: ... com.sun.tools.javac.parser ...`
→ Custom VM Options에 `--add-exports` 라인 누락. 위 3번 재확인.

### CI에서 `spotlessCheck FAILED`
→ 로컬에서 `./gradlew spotlessApply` 돌리고 결과를 커밋. PR 다시 push.

### Spotless가 generated 파일(QueryDSL Q 클래스)을 만지려고 함
→ `build.gradle`의 `targetExclude '**/build/**'`로 이미 제외됨. 새 생성 디렉토리가 생기면 같이 추가.

### 메서드 체인 들여쓰기가 8 spaces로 보임
→ AOSP의 의도된 동작 (continuation indent). 첫 들여쓰기 4 + 연속 추가 4 = 8.

## 왜 google-java-format (AOSP) 인가?

- **import 정렬을 강제** — Palantir는 import 정렬을 안 해서 IDE와 충돌(ping-pong)이 발생했음. AOSP는 자동 정렬
- **IntelliJ Default Code Style과 호환** — 별도 플러그인 없이도 거의 일치
- **4-space 유지** — AOSP variant가 Android Open Source Project 룰. 100 column wrap
- **널리 사용** — Google 표준이라 IDE/도구 통합이 가장 좋음

## 변경 이력
- 2026-06-04 도입. Spotless + Palantir 2.50 → Palantir 2.89 → google-java-format AOSP로 변경. Import 정렬 강제를 위한 최종 결정.
