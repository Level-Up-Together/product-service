# 검색엔진 등록 검토 (Google · 네이버 · Bing · 다음)

작성 2026-11-12. 웹(`https://level-up-together.com`) 인지도를 올리려고 검색엔진 관리 도구에 사이트를 등록하는 일을 검토한 기록이다.
아래 "현재 상태"는 이날 운영 사이트에 직접 요청해서 확인한 값이다.

## 진행 현황 (LUT-798)

| 검색엔진 | 상태 | 계정 | 확인 방법 |
|---|---|---|---|
| Google Search Console | **등록 끝** (2026-11-12) — 소유 확인, 사이트맵 제출(성공, 페이지 1개), GA4 연결 | `ceo@pink-spider.io` (GA4 계정과 같다) | 도메인 속성 + DNS TXT |
| Bing Webmaster Tools | **등록 끝** (2026-11-12) — 사이트 `https://level-up-together.com/` 추가, 사이트맵 제출(처리 중으로 표시, 오류 0) | `ceo@pink-spider.io` | Google Search Console 에서 가져오기 |
| 네이버 서치어드바이저 | 값은 웹 코드에 넣음(`epic/v1.1.6`). **v1.1.6 운영 배포 뒤** 서치어드바이저에서 "소유확인"을 누르고 사이트맵을 제출한다 | 유저 계정 | HTML 메타 태그 |
| 다음 웹마스터도구 | 값은 웹 코드에 넣음(`epic/v1.1.6`). **v1.1.6 운영 배포 뒤** 웹마스터도구에서 인증을 마친다 | 유저 계정 | `robots.txt` 줄 |

웹 코드 위치 (LUT-798)

- 확인 값: 웹 `src/lib/utils/site-verification.ts` (공개 값이다. 지우면 소유 확인이 풀린다)
- 네이버: 루트 `src/app/layout.tsx` 의 `metadata.verification` → 모든 페이지에 `<meta name="naver-site-verification">`
- 다음: `src/app/robots.txt/route.ts` 가 `#DaumWebMasterTool:` 줄을 싣는다. 주석 줄을 내야 해서 메타데이터 라우트(`robots.ts`)를 라우트 핸들러로 바꿨다. 크롤링 규칙은 그대로다.

후속 티켓

- **LUT-799** 랜딩 언어별 주소 + hreflang (아래 "문제 1")
- **LUT-800** 공개 페이지 검색 노출 (아래 "문제 2")

Google 등록 기록

- DNS: 인프라 레포 `route53.tf` 의 `aws_route53_record.apex_txt`. 루트 도메인의 TXT 는 레코드 세트가 하나뿐이라, 값이 늘면(SPF·다른 소유 확인) 이 목록에 더한다.
- **이 TXT 레코드를 지우면 소유 확인이 풀린다.** Search Console 이 주기적으로 다시 확인한다.
- Search Console 속성 이름은 `level-up-together.com` (도메인 속성 — `www`·`dev`·`admin` 같은 하위 도메인과 http/https 를 모두 포함한다).
- GA4 운영 속성(523226971)의 웹 스트림 "Level Up Together Prod Web App"과 연결했다(2026-11-12, GA 관리 > 제품 링크 > Search Console 링크). 검색어 데이터는 며칠 뒤부터 보인다.

## 결론

- 등록은 한다. 비용이 없고, 사람이 할 일은 1시간 정도다.
- 등록만으로 인지도가 크게 오르지는 않는다. 등록은 검색엔진에 "이 사이트가 있다"고 알리는 일이다. 검색 결과 순위는 페이지 내용과 바깥 링크가 정한다.
- 지금 사이트에는 등록 효과를 줄이는 문제가 두 가지 있다(아래 "문제"). 등록 다음에 코드 작업으로 고친다.

## 등록 대상은 `/home` 이 아니라 도메인 전체

- `/home` 은 LUT-436 때 일부러 검색에서 뺐다. 응답 헤더에 `X-Robots-Tag: noindex`(검색 결과에 넣지 말라는 표시)가 붙는다.
  - 코드: 웹 `src/middleware.ts` (`pathname === '/home'` 분기)
  - 이유: `/home` 은 로그아웃 상태 피드라서 대표 페이지가 아니다. 검색 노출은 루트(랜딩)로 모은다.
  - `robots.txt` 로 `/home` 을 막지 않는 이유: 막으면 크롤러가 noindex 헤더를 읽지 못하고, 외부 링크만으로 색인될 수 있다.
- 검색 대표 페이지는 랜딩 `https://level-up-together.com/` 이다.
- 그래서 각 도구에는 `level-up-together.com` 도메인 전체를 등록한다.

## 현재 상태 (2026-11-12 확인)

| 항목 | 상태 |
|---|---|
| 등록 흔적 | 없음. DNS TXT 레코드(도메인에 붙이는 확인용 글자) 없음. 웹 코드에 확인용 메타 태그·파일 없음. Search Console 에 도메인 속성이 추가만 돼 있고 "확인 안됨"이었다 (→ 같은 날 Google 등록, 위 "진행 현황") |
| `robots.txt` | 200. 전체 허용, `/api/` 만 차단, 사이트맵 주소 포함 (웹 `src/app/robots.ts`) |
| 사이트맵 `sitemap.xml` | 200. `/` 1개만 (웹 `src/app/sitemap.ts`) |
| 랜딩 언어 | 같은 주소가 요청 헤더 `Accept-Language` 에 따라 ko·en·ja 로 바뀐다. 헤더 없이 오면 영어 |
| hreflang(언어별 페이지를 서로 이어 주는 표시) | 없음 |
| canonical(대표 주소 표시) | 랜딩에 `https://level-up-together.com` |
| 공개 페이지 `/ranking` `/shop` `/tower` `/season` | 200, 검색 차단 없음. 제목이 전부 `Level Up Together` 로 같다. 사이트맵에 없다 |
| DNS | Route53. 인프라 레포 `level-up-together-infra/route53.tf` 에서 Terraform 으로 관리 |

- GA(구글 애널리틱스) 연결 같은, 위에서 보이지 않는 방법으로 이미 등록됐을 수는 있다. 각 도구에서 한 번 확인한다. Google 은 확인했다(등록 안 돼 있었다). Bing 도 같은 날 등록했다. 네이버·다음은 사이트 추가까지 했고 소유 확인은 운영 배포 뒤다.
- 2026-11-11 ASO 점검 때도 검색엔진 미등록으로 봤다.

## 문제

### 1. 검색엔진은 영어 랜딩만 볼 가능성이 크다

- 네이버 크롤러(Yeti) 이름으로 요청했을 때 영어 페이지가 왔다.
- Google 문서에 따르면 Google 크롤러도 보통 `Accept-Language` 없이 온다. 한 주소에서 언어가 바뀌는 페이지는 언어별 주소를 따로 두라고 권한다.
- 결과: 한국어·일본어 설명은 검색엔진에 저장되지 않는다. 네이버에서 "레벨업투게더"를 검색해도 영어 설명이 나올 수 있다.
- **2026-11-12 Search Console "URL 검사"로 확인했다.** Google 이 저장한 `/` 는 `<html lang="en">` 이고 제목이 영어(`Level Up Together (레벨업투게더) — The gamified productivity app`)다.
  - `/` 는 이미 색인돼 있다. 최근 크롤링 2026-11-05 18:42, 크롤러는 Googlebot 스마트폰, 표준 URL 은 `https://level-up-together.com/`.
  - Google 이 아는 바깥 링크(참조 페이지)는 2개다: `hada-type.com/levelup`, `kernelpanicpodcast.com/all/1718/33.html`.
  - 저장된 버전에서 페이지 리소스 51개 중 26개(글꼴·랜딩 이미지)가 "기타 오류"로 안 읽혔다. Google 이 색인용 크롤링에서 리소스를 다 받지 않을 때 흔히 나오는 표시다. 실제 문제인지는 "실제 URL 테스트"로 따로 봐야 한다(아직 안 봄).

### 2. 검색에 걸릴 페이지가 사실상 1개다

- 미션·길드 내용은 로그인 뒤에 있어 크롤러가 볼 수 없다.
- 공개 페이지 4개는 제목이 같다. 지금 사이트맵에 넣으면 같은 페이지가 여러 개 있는 것처럼 보일 수 있다.
- **2026-11-12 추가 확인: 공개 페이지 4개는 서버가 준 HTML 에 내용이 거의 없다.** 로그인 없이 받은 본문이 `/ranking` 7자, `/shop` 24~42자, `/tower` "로딩 중...", `/season` 0자다(랜딩은 1,900~3,500자). 내용은 브라우저에서 API 를 불러 그린다.
  - 그래서 제목을 바꾸고 사이트맵에 넣는 것만으로는 부족하다. 서버에서 내용을 미리 그려야 검색엔진이 읽는다. → LUT-800

## 추천 순서

### 1단계: 등록 (대부분 사람 몫)

1. **Google Search Console**: "도메인" 방식으로 사이트를 추가하고 TXT 값을 받는다. TXT 값은 `route53.tf` 에 넣는다. — 끝(2026-11-12)
2. Search Console 에 사이트맵 `https://level-up-together.com/sitemap.xml` 을 제출한다. — 끝
3. Search Console "URL 검사"로 `/` 를 Google 이 어떻게 보는지 확인한다. — 끝(영어로 저장돼 있다, 아래 "문제 1")
4. Search Console 을 GA4 운영 속성(523226971)과 연결한다. 검색어 보고서를 볼 수 있다. — 끝(2026-11-12)
5. **Bing Webmaster Tools**: "Google Search Console 에서 가져오기"를 고른다. 확인 절차가 넘어간다. **사이트맵은 넘어오지 않았다** — Sitemaps 화면에서 따로 제출해야 한다. — 끝(2026-11-12)
6. **네이버 서치어드바이저**: 사이트를 추가하고 확인용 HTML 태그나 파일을 받는다.
   - 웹 코드에 넣고 배포해야 한다. 메타 태그는 Next.js `metadata.verification.other`, 파일은 `public/` 에 둔다.
   - 네이버에도 사이트맵을 제출한다.
7. **다음(Daum) 웹마스터도구**: `robots.txt` 에 확인용 줄 하나를 넣는다. 한국 사용자 비중은 작지만 비용이 없다.

### 2단계: 등록 효과를 키우는 코드 작업 (티켓 1~2건)

1. **언어마다 주소를 나누고 hreflang 을 붙인다.** (LUT-799)
   - 예: `/ko`, `/ja` (지금은 404). 주소 모양은 티켓에서 정한다.
   - 검색엔진이 언어별 랜딩을 따로 저장한다. 한국어 검색 노출에 가장 큰 영향을 준다.
2. **공개 페이지를 검색엔진이 읽을 수 있게 만든다.** (LUT-800)
   - 노출할 페이지를 고르고, 서버에서 내용을 미리 그리고, 페이지마다 다른 제목·설명을 달고, 사이트맵에 넣는다.
   - 처음 검토 때는 "제목만 바꾸면 검색에 걸리는 페이지가 5개로 늘어난다"고 봤다. 서버 HTML 에 내용이 없어서 그것만으로는 안 된다(위 "문제 2").

### 3단계: 인지도 자체

- 등록은 이미 이름을 아는 사람이 검색했을 때 잘 나오게 하는 정도다.
- 이름을 모르는 사람이 찾아오게 하려면 바깥 링크가 필요하다. 예: 공식 블로그 글, 앱 소개 커뮤니티 글, 스토어 페이지의 사이트 링크.
- 네이버는 검색 결과에서 블로그·카페 비중이 크다. 그래서 네이버에서는 등록보다 블로그 글 효과가 클 수 있다(일반적인 경향이며 우리 사이트에서 확인한 사실은 아니다).

## 역할

| 누가 | 할 일 |
|---|---|
| 사람 | 각 도구에 로그인해 사이트 추가, 확인 값 받기, 사이트맵 제출 |
| Claude | TXT 레코드(Terraform), 네이버 확인 태그, 다음 확인 줄, 2단계 티켓 작성·구현 |

## 다시 확인하는 명령

```bash
# robots.txt · sitemap.xml
curl -s https://level-up-together.com/robots.txt
curl -s https://level-up-together.com/sitemap.xml

# /home 의 noindex 헤더
curl -s -o /dev/null -D - https://level-up-together.com/home | grep -i x-robots

# 언어별 랜딩 제목 (헤더 없음 = 크롤러가 보는 값)
for lang in "" ko en ja; do
  curl -s ${lang:+-H "Accept-Language: $lang"} https://level-up-together.com/ | grep -o '<title>[^<]*</title>'
done

# 검색엔진 확인용 DNS TXT 레코드
dig +short TXT level-up-together.com
```
