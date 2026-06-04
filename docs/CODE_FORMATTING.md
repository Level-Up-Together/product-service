# 코드 포맷팅 가이드 (Spotless / Prettier)

전 저장소(5개)에서 자동 포맷팅을 강제합니다. 본 문서는 **로컬 개발 환경 설정 + 일상 사용 + 트러블슈팅**을 다룹니다.

## 적용 범위

| 저장소                                | 도구                                   | 스타일                                   | 명령                        |
|------------------------------------|--------------------------------------|---------------------------------------|---------------------------|
| `level-up-together-platform`       | Spotless + palantir-java-format 2.50 | Palantir Java Style (4-space, 120col) | `./gradlew spotlessApply` |
| `product-service`                  | 동일                                   | 동일                                    | `./gradlew spotlessApply` |
| `admin-service`                    | 동일                                   | 동일                                    | `./gradlew spotlessApply` |
| `level-up-together-frontend`       | Prettier 3 + eslint-config-prettier  | tab, single-quote, 100col             | `pnpm format`             |
| `level-up-together-admin-frontend` | 동일                                   | 2-space, double-quote, 120col         | `pnpm format`             |

CI(GitHub Actions)는 push 시 `spotlessCheck` / `pnpm format:check`를 실행합니다. **위반 시 빌드 실패** → PR 머지 불가.

## IDE 설정 (필수)
   
IDE on-save 포맷터가 Palantir/Prettier와 **같은 결과**를 내도록 맞추지 않으면 매 저장마다 포맷이 흔들립니다(ping-pong). 아래 설정을 반드시 적용해주세요.

### IntelliJ IDEA (Java 저장소)

1. **Palantir Java Format 플러그인 설치**
  - `Settings → Plugins → Marketplace`에서 `palantir-java-format` 검색 후 설치
  - IntelliJ 재시작

2. **플러그인 활성화**
  - `Settings → Other Settings → palantir-java-format Settings`
  - `Enable palantir-java-format` 체크
  - `Default formatter` 로 지정

3. **JDK 17+ 내부 API 접근 허용** (IntelliJ가 자체 임베디드 JRE를 쓰므로 필요)
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

4. **저장 시 자동 정리 켜기** (선택이지만 권장)
  - `Settings → Tools → Actions on Save`
  - `Reformat code` ✓
  - `Optimize imports` ✓

5. **확인**: 아무 Java 파일을 열고 `Cmd+Opt+L`(Mac) / `Ctrl+Alt+L`(Win) 후 `./gradlew spotlessCheck`가 통과하면 OK

### VSCode / Cursor (Frontend 저장소)

1. **Prettier - Code formatter** 확장 설치 (`esbenp.prettier-vscode`)
2. 워크스페이스 `.vscode/settings.json` (없으면 만들기):
   ```json
   {
     "editor.defaultFormatter": "esbenp.prettier-vscode",
     "editor.formatOnSave": true,
     "[typescript]": { "editor.defaultFormatter": "esbenp.prettier-vscode" },
     "[typescriptreact]": { "editor.defaultFormatter": "esbenp.prettier-vscode" },
     "[javascript]": { "editor.defaultFormatter": "esbenp.prettier-vscode" },
     "[javascriptreact]": { "editor.defaultFormatter": "esbenp.prettier-vscode" },
     "[json]": { "editor.defaultFormatter": "esbenp.prettier-vscode" },
     "[css]": { "editor.defaultFormatter": "esbenp.prettier-vscode" },
     "[scss]": { "editor.defaultFormatter": "esbenp.prettier-vscode" }
   }
   ```
3. **확인**: 아무 ts/tsx 파일 저장 후 `pnpm format:check`가 통과하면 OK

### WebStorm (Frontend 저장소)

WebStorm 2020.1+ 는 Prettier를 **내장 지원**합니다. 별도 플러그인 설치는 불필요하지만 활성화는 필요.

1. **Prettier 설정 열기**
  - `Settings → Languages & Frameworks → JavaScript → Prettier`

2. **Prettier package 지정**
  - `Prettier package`에 `<프로젝트>/node_modules/prettier` 자동 감지됨 (안 되면 직접 지정)
  - `Run for files`는 기본값 `{**/*,*}.{js,ts,jsx,tsx,mjs,cjs,vue,html,css,scss}` 그대로 두거나 필요시 `.json,.md` 추가

3. **자동 적용 체크박스 켜기**
  - ✓ `On 'Reformat Code' action` — `Cmd+Opt+L` 누를 때 Prettier 적용
  - ✓ `On save` — 저장할 때 자동 Prettier (권장)
  - ✓ `Run on save for files` 패턴이 위 "Run for files"와 일치하는지 확인

4. **JetBrains 자체 포매터 비활성화** (충돌 방지)
  - WebStorm 자체의 코드 스타일이 Prettier와 다르면 Reformat 시 결과가 한쪽 → 다른 쪽으로 튐
  - `Settings → Editor → Code Style → TypeScript / JavaScript`에서 `Set from... → Prettier`로 한 번 동기화 하면 양쪽 결과 거의 일치
  - 또는 `Settings → Tools → Actions on Save`에서 `Reformat code`는 끄고 `Run Prettier` 만 켜기 (가장 안전)

5. **JSX 따옴표 스타일 추가 가드** (선택)
  - WebStorm의 JSX 자동 따옴표가 Prettier와 다르면 작성 중에도 어긋남
  - `Settings → Editor → Code Style → JavaScript/TypeScript → Punctuation`에서 따옴표 스타일을 프로젝트 `.prettierrc.json`(
    admin-frontend: double, frontend: single)와 맞추기

6. **확인**: 아무 ts/tsx 파일 저장 후 `pnpm format:check`가 통과하면 OK

> **팁**: WebStorm은 `.prettierignore`도 자동으로 존중합니다. `.prettierrc.json` 변경 후 IDE 캐시가 안 잡히면 `File → Invalidate Caches`.

## 일상 사용

### 수동 포맷 (대량 변경 후 한 번에)

```bash
# Java 저장소
./gradlew spotlessApply

# Frontend 저장소
pnpm format
```

### CI에 올리기 전 검사

```bash
# Java
./gradlew spotlessCheck

# Frontend
pnpm format:check
```

### 단일 파일만

```bash
# Java
./gradlew :service:spotlessJavaApply -PspotlessFiles=$(realpath path/to/File.java)

# Frontend
pnpm exec prettier --write path/to/File.tsx
```

## 트러블슈팅

### IntelliJ에서 저장할 때마다 포맷이 변함 / spotlessCheck 와 다른 결과

→ Palantir 플러그인 설치 + Default formatter 지정이 안 됨. 위 IDE 설정 1~3 단계 다시 확인.

### `IllegalAccessError: ... com.sun.tools.javac.parser ...`

→ Custom VM Options에 `--add-exports` 라인 누락. 위 3번 다시.

### CI에서 `spotlessCheck FAILED` 뜸

→ 로컬에서 `./gradlew spotlessApply` 돌리고 결과를 커밋. PR 다시 push.

### Frontend에서 저장 시 다른 포맷터가 동작

→ VSCode/Cursor `editor.defaultFormatter`가 다른 확장으로 설정됨. 워크스페이스 설정이 사용자 설정을 덮어쓰도록 위 .vscode/settings.json 적용.

### 새 파일 만들 때 Palantir/Prettier 규칙 위반

→ 파일 저장 한 번 → 자동 포맷. 또는 위 단일 파일 명령 실행.

### Spotless가 generated 파일(QueryDSL Q 클래스 등)을 만지려고 함

→ `build.gradle`에서 `targetExclude '**/build/**', '**/build/generated/**'`로 이미 제외됨. 새 생성 디렉토리 추가 시 같이 추가.

## 왜 Palantir / Prettier 인가?

| 기준              | 결정                                                                                       |
|-----------------|------------------------------------------------------------------------------------------|
| Java 포매터        | google-java-format(2-space) 대신 Palantir(4-space) 선택 — 기존 IntelliJ 4-space 코드 베이스와 가장 가까움 |
| Frontend 포매터    | ESLint만으로는 코드 스타일 통일 한계 → Prettier 도입                                                    |
| CI 강제           | 팀원 IDE 설정 차이로 인한 ping-pong 커밋을 사전 차단                                                     |
| pre-commit hook | 별도 도입 안 함 (개인 자유) — CI에서 잡힘                                                              |

## 변경 이력

- 2026-06-04 도입. 5개 저장소 첫 일괄 reformat.
