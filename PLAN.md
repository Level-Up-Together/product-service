# 시즌 순위별 보상 칭호 시스템 구현 계획

## 개요
시즌 관리에서 순위별 보상 칭호를 설정하고, 시즌 종료 시 자동으로 칭호를 부여하는 기능

## 핵심 요구사항
1. **칭호 생성**: 시즌 모달에서 새 칭호를 직접 생성 (기존 칭호 선택 X)
2. **희귀도 필수**: 칭호 생성 시 희귀도(Rarity) 선택 필수
3. **랭킹 구분**: 전체 랭킹 + 카테고리별 랭킹 지원
4. **순위 범위**: 단일(1위) 또는 범위(3-10위) 설정 가능
5. **자동 부여**: 시즌 종료 시 자동 칭호 부여 + 알림 발송

## 1단계: 데이터베이스 스키마 변경

### Admin DB - season_rank_reward 테이블 수정
```sql
ALTER TABLE season_rank_reward
ADD COLUMN category_id BIGINT NULL,
ADD COLUMN category_name VARCHAR(100) NULL;

COMMENT ON COLUMN season_rank_reward.category_id IS 'NULL이면 전체 랭킹, 값이 있으면 해당 카테고리 랭킹';
```

### Product DB - season_rank_reward 테이블 수정
```sql
ALTER TABLE season_rank_reward
ADD COLUMN category_id BIGINT NULL,
ADD COLUMN category_name VARCHAR(100) NULL;
```

### Product DB - season_reward_history 테이블 수정
```sql
ALTER TABLE season_reward_history
ADD COLUMN category_id BIGINT NULL,
ADD COLUMN category_name VARCHAR(100) NULL;
```

## 2단계: Admin Backend 변경

### 2.1 SeasonRankReward 엔티티 수정
파일: `level-up-together-mvp-admin/src/.../adminservice/seasonrankreward/domain/SeasonRankReward.java`

```java
@Column(name = "category_id")
private Long categoryId;  // null이면 전체 랭킹

@Column(name = "category_name")
private String categoryName;  // 카테고리명 (조회 편의)
```

### 2.2 시즌 보상 칭호 생성 Request DTO
파일: `CreateSeasonRankRewardRequest.java`

```java
public record CreateSeasonRankRewardRequest(
    @NotNull Long seasonId,
    @NotNull Integer rankStart,
    @NotNull Integer rankEnd,
    Long categoryId,           // null이면 전체 랭킹
    String categoryName,
    // 새 칭호 생성 정보
    @NotNull String titleName,
    @NotNull TitleRarity titleRarity,  // COMMON, UNCOMMON, RARE, EPIC, LEGENDARY, MYTHIC
    @NotNull TitlePositionType titlePositionType  // LEFT, RIGHT
) {}
```

### 2.3 벌크 생성 API
파일: `SeasonRankRewardController.java`

```java
@PostMapping("/bulk")
public ApiResult<List<SeasonRankRewardResponse>> createBulk(
    @Valid @RequestBody List<CreateSeasonRankRewardRequest> requests
) {
    return ApiResult.ok(service.createBulk(requests));
}
```

### 2.4 SeasonRankRewardService 수정
- createBulk() 메서드: Title 생성 → SeasonRankReward 생성을 한 트랜잭션에서 처리
- Title 생성 시 acquisitionType = SEASON 설정

## 3단계: Product Backend 변경

### 3.1 SeasonRankReward 엔티티 수정
파일: `level-up-together-mvp/src/.../gamificationservice/seasonrankreward/domain/SeasonRankReward.java`

동일하게 categoryId, categoryName 필드 추가

### 3.2 SeasonRewardHistory 엔티티 수정
파일: `SeasonRewardHistory.java`

```java
@Column(name = "category_id")
private Long categoryId;

@Column(name = "category_name")
private String categoryName;
```

### 3.3 SeasonRewardProcessorService 수정 (핵심)
파일: `SeasonRewardProcessorService.java`

```java
@Transactional
public void processSeasonEnd(Season season) {
    List<SeasonRankReward> rewards = rewardRepository.findBySeasonId(season.getId());

    // 전체 랭킹 보상 처리
    List<SeasonRankReward> overallRewards = rewards.stream()
        .filter(r -> r.getCategoryId() == null)
        .toList();
    processOverallRankingRewards(season, overallRewards);

    // 카테고리별 랭킹 보상 처리
    Map<Long, List<SeasonRankReward>> categoryRewards = rewards.stream()
        .filter(r -> r.getCategoryId() != null)
        .collect(Collectors.groupingBy(SeasonRankReward::getCategoryId));

    categoryRewards.forEach((categoryId, catRewards) ->
        processCategoryRankingRewards(season, categoryId, catRewards)
    );
}

private void processCategoryRankingRewards(Season season, Long categoryId, List<SeasonRankReward> rewards) {
    String categoryName = rewards.get(0).getCategoryName();

    // 카테고리별 경험치 랭킹 조회
    List<Object[]> rankings = experienceHistoryRepository
        .findTopExpGainersByCategoryAndPeriod(
            categoryName,
            season.getStartDate().atStartOfDay(),
            season.getEndDate().atTime(23, 59, 59),
            PageRequest.of(0, getMaxRank(rewards))
        );

    // 순위에 맞는 보상 부여
    for (int rank = 1; rank <= rankings.size(); rank++) {
        Long userId = (Long) rankings.get(rank - 1)[0];
        grantRewardForRank(userId, rank, rewards, season, categoryId, categoryName);
    }
}
```

## 4단계: Admin Frontend 변경

### 4.1 SeasonFormModal 수정
파일: `level-up-together-admin-frontend/src/app/(afterLogin)/season/page.tsx`

```tsx
// 보상 목록 상태
const [rewards, setRewards] = useState<SeasonRewardForm[]>([]);

interface SeasonRewardForm {
  id?: number;
  rankType: 'overall' | 'category';  // 전체 or 카테고리별
  categoryId?: number;
  categoryName?: string;
  rankStart: number;
  rankEnd: number;
  // 칭호 생성 정보
  titleName: string;
  titleRarity: TitleRarity;
  titlePositionType: TitlePositionType;
}

// 희귀도 선택 UI (드롭다운)
<select value={reward.titleRarity} onChange={...}>
  <option value="COMMON">일반 (Common)</option>
  <option value="UNCOMMON">고급 (Uncommon)</option>
  <option value="RARE">희귀 (Rare)</option>
  <option value="EPIC">영웅 (Epic)</option>
  <option value="LEGENDARY">전설 (Legendary)</option>
  <option value="MYTHIC">신화 (Mythic)</option>
</select>
```

### 4.2 보상 추가 UI
```tsx
// 동적 보상 추가 폼
<button onClick={addReward}>+ 보상 추가</button>

{rewards.map((reward, index) => (
  <div key={index} className="reward-item">
    {/* 랭킹 타입 선택 */}
    <select value={reward.rankType}>
      <option value="overall">전체 랭킹</option>
      <option value="category">카테고리별 랭킹</option>
    </select>

    {/* 카테고리 선택 (카테고리별일 때만) */}
    {reward.rankType === 'category' && (
      <select value={reward.categoryId}>
        {categories.map(cat => (
          <option key={cat.id} value={cat.id}>{cat.name}</option>
        ))}
      </select>
    )}

    {/* 순위 범위 */}
    <input type="number" value={reward.rankStart} placeholder="시작 순위" />
    <input type="number" value={reward.rankEnd} placeholder="종료 순위" />

    {/* 칭호 정보 */}
    <input type="text" value={reward.titleName} placeholder="칭호명" />

    {/* 희귀도 선택 (필수) */}
    <select value={reward.titleRarity} required>
      <option value="">희귀도 선택</option>
      <option value="COMMON">일반</option>
      <option value="UNCOMMON">고급</option>
      <option value="RARE">희귀</option>
      <option value="EPIC">영웅</option>
      <option value="LEGENDARY">전설</option>
      <option value="MYTHIC">신화</option>
    </select>

    {/* 위치 선택 */}
    <select value={reward.titlePositionType}>
      <option value="LEFT">이름 앞</option>
      <option value="RIGHT">이름 뒤</option>
    </select>

    <button onClick={() => removeReward(index)}>삭제</button>
  </div>
))}
```

## 5단계: 테스트 코드 작성

### 5.1 Admin Backend 테스트
- SeasonRankRewardServiceTest: 벌크 생성, 칭호 생성 연동 테스트

### 5.2 Product Backend 테스트
- SeasonRewardProcessorServiceTest: 전체/카테고리별 보상 처리 테스트

## 구현 순서
1. DB 마이그레이션 SQL 작성 및 실행
2. Admin Backend - 엔티티, DTO, Service, Controller 수정
3. Product Backend - 엔티티, Service 수정
4. Admin Frontend - SeasonFormModal 보상 관리 UI 추가
5. 테스트 코드 작성
6. 통합 테스트

## 주의사항
- 희귀도(TitleRarity)는 칭호 생성 시 **필수 입력**
- 카테고리별 랭킹 조회 시 categoryName으로 조회 (ExperienceHistory에 categoryName 저장됨)
- 트랜잭션 매니저: Admin은 adminTransactionManager, Product은 gamificationTransactionManager 사용
