# 검색엔진 등록 검토 (Google · 네이버 · Bing · 다음)

작성 2026-11-12. 웹(`https://level-up-together.com`) 인지도를 올리려고 검색엔진 관리 도구에 사이트를 등록하는 일을 검토한 기록이다.
아래 "현재 상태"는 이날 운영 사이트에 직접 요청해서 확인한 값이다.

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
| 등록 흔적 | 없음. DNS TXT 레코드(도메인에 붙이는 확인용 글자) 없음. 웹 코드에 확인용 메타 태그·파일 없음 |
| `robots.txt` | 200. 전체 허용, `/api/` 만 차단, 사이트맵 주소 포함 (웹 `src/app/robots.ts`) |
| 사이트맵 `sitemap.xml` | 200. `/` 1개만 (웹 `src/app/sitemap.ts`) |
| 랜딩 언어 | 같은 주소가 요청 헤더 `Accept-Language` 에 따라 ko·en·ja 로 바뀐다. 헤더 없이 오면 영어 |
| hreflang(언어별 페이지를 서로 이어 주는 표시) | 없음 |
| canonical(대표 주소 표시) | 랜딩에 `https://level-up-together.com` |
| 공개 페이지 `/ranking` `/shop` `/tower` `/season` | 200, 검색 차단 없음. 제목이 전부 `Level Up Together` 로 같다. 사이트맵에 없다 |
| DNS | Route53. 인프라 레포 `level-up-together-infra/route53.tf` 에서 Terraform 으로 관리 |

- GA(구글 애널리틱스) 연결 같은, 위에서 보이지 않는 방법으로 이미 등록됐을 수는 있다. 각 도구에서 한 번 확인한다.
- 2026-11-11 ASO 점검 때도 검색엔진 미등록으로 봤다.

## 문제

### 1. 검색엔진은 영어 랜딩만 볼 가능성이 크다

- 네이버 크롤러(Yeti) 이름으로 요청했을 때 영어 페이지가 왔다.
- Google 문서에 따르면 Google 크롤러도 보통 `Accept-Language` 없이 온다. 한 주소에서 언어가 바뀌는 페이지는 언어별 주소를 따로 두라고 권한다.
- 결과: 한국어·일본어 설명은 검색엔진에 저장되지 않는다. 네이버에서 "레벨업투게더"를 검색해도 영어 설명이 나올 수 있다.
- 등록 뒤 Search Console "URL 검사"에서 `/` 가 영어로 보이면 이 문제가 사실로 확인된다.

### 2. 검색에 걸릴 페이지가 사실상 1개다

- 미션·길드 내용은 로그인 뒤에 있어 크롤러가 볼 수 없다.
- 공개 페이지 4개는 제목이 같다. 지금 사이트맵에 넣으면 같은 페이지가 여러 개 있는 것처럼 보일 수 있다.

## 추천 순서

### 1단계: 등록 (대부분 사람 몫)

1. **Google Search Console**: "도메인" 방식으로 사이트를 추가하고 TXT 값을 받는다. TXT 값은 `route53.tf` 에 넣는다.
2. Search Console 에 사이트맵 `https://level-up-together.com/sitemap.xml` 을 제출한다.
3. Search Console "URL 검사"로 `/` 를 Google 이 어떻게 보는지 확인한다.
4. Search Console 을 GA4 운영 속성(523226971)과 연결한다. 검색어 보고서를 볼 수 있다.
5. **Bing Webmaster Tools**: "Google Search Console 에서 가져오기"를 고른다. 확인 절차와 사이트맵이 한 번에 넘어간다.
6. **네이버 서치어드바이저**: 사이트를 추가하고 확인용 HTML 태그나 파일을 받는다.
   - 웹 코드에 넣고 배포해야 한다. 메타 태그는 Next.js `metadata.verification.other`, 파일은 `public/` 에 둔다.
   - 네이버에도 사이트맵을 제출한다.
7. **다음(Daum) 웹마스터도구**: `robots.txt` 에 확인용 줄 하나를 넣는다. 한국 사용자 비중은 작지만 비용이 없다.

### 2단계: 등록 효과를 키우는 코드 작업 (티켓 1~2건)

1. **언어마다 주소를 나누고 hreflang 을 붙인다.**
   - 예: `/ko`, `/ja` (지금은 404). 주소 모양은 티켓에서 정한다.
   - 검색엔진이 언어별 랜딩을 따로 저장한다. 한국어 검색 노출에 가장 큰 영향을 준다.
2. **공개 페이지 4개에 페이지마다 다른 제목·설명을 달고 사이트맵에 넣는다.**
   - 검색에 걸리는 페이지가 1개에서 5개로 늘어난다.

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
