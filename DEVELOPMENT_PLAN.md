# Burp Suite IDOR/BOLA Checker Extension - 개발 계획서 (v3)

## 목적

Burp Suite 사이트맵(Sitemap)에 존재하는 모든 HTTP 요청에 대해 다수의 인증 세션(쿠키/헤더)을 교체하여 재전송하고,  
응답 바디의 차이(Difference)를 비교함으로써 **IDOR(Insecure Direct Object Reference)** 및 **BOLA(Broken Object Level Authorization)** 취약점을 탐지합니다.

---

## 1. 기술 스택

| 항목 | 선택 |
|------|------|
| 언어 | Java 17+ |
| Burp API | Montoya API (burp-extender-api 2023.x+) |
| 빌드 | Gradle (Shadow JAR) |
| UI | Java Swing (Burp 기본 스타일) |
| 비교 알고리즘 | 응답 바디 해시 비교 (헤더 제외) |

---

## 2. 전체 아키텍처

```
┌────────────────────────────────────────────────────────┐
│                    Burp Suite UI                        │
│  Sitemap 우클릭 → "IDOR/BOLA Check (Multi-Session)"     │
└─────────────────────┬──────────────────────────────────┘
                      │ Context Menu Action
                      ▼
┌────────────────────────────────────────────────────────┐
│             SitemapContextMenuProvider                  │
│  - 선택된 호스트/경로의 모든 요청 수집                   │
└─────────────────────┬──────────────────────────────────┘
                      │
                      ▼
┌────────────────────────────────────────────────────────┐
│             RequestDeduplicator (핵심 모듈)             │
│  - Fingerprint 기반 중복 제거 (원본 URL 유지)            │
│  - 중복 제거 OFF 시 전체 요청 그대로 통과                │
│  - 정적 자원 필터 (설정 가능)                           │
│  - 사용자 정의 제외 엔드포인트 필터                     │
│  - 그룹핑 정보 보존 (제외된 URL 목록 포함)              │
└─────────────────────┬──────────────────────────────────┘
                      │ 정제된 요청 목록 + 그룹핑 메타데이터
                      ▼
┌────────────────────────────────────────────────────────┐
│             MultiSessionReplayer                        │
│  - 모든 프로필(User A, B, C...) 동시 재전송             │
│  - User A도 새로 재전송 → 동일 시점 Baseline 확보       │
│  - ThreadPool 병렬 처리                                 │
│  - 진행 상황 이벤트 발행 (Progress)                     │
└─────────────────────┬──────────────────────────────────┘
                      │ 프로필별 응답 세트 (동시 수집)
                      ▼
┌────────────────────────────────────────────────────────┐
│             ResponseComparator                          │
│  - 응답 바디만 해시 비교 (헤더 제외)                     │
│  - Content-Type 인식 기반 정규화                        │
│  - 판정: SAME / SIMILAR / DIFFERENCE                    │
└─────────────────────┬──────────────────────────────────┘
                      │
                      ▼
┌────────────────────────────────────────────────────────┐
│             ResultPanel (UI)                            │
│  - 실시간 결과 테이블 (스캔 중 업데이트)                 │
│  - 진행률 표시 + 일시정지/취소 버튼                     │
│  - 상세 Diff 뷰어                                       │
│  - CSV 내보내기                                         │
└────────────────────────────────────────────────────────┘
```

---

## 3. 핵심 모듈 상세 설계

### 3.1 RequestDeduplicator - 중복 패킷 필터링 알고리즘

**설계 원칙:**
- 정규화(Normalization)는 **Fingerprint 계산 전용** — 실제 재전송에는 원본 URL 그대로 사용
- **중복 제거 ON/OFF** 전환 가능 (대시보드 설정)
- 제거된 요청의 **그룹핑 정보를 보존**하여 UI에서 드릴다운 확인 가능

#### Step 1: Fingerprint용 URL 정규화 (재전송에는 미사용)

```
[Fingerprint 계산에만 사용 — 원본 URL은 변경하지 않음]

원본 URL: https://example.com/api/user/123?page=1&sort=asc&_t=1699999999
Fingerprint용 정규화: Method + "/api/user/{id}" + "page|sort"

재전송 URL: https://example.com/api/user/123?page=1&sort=asc&_t=1699999999  ← 그대로
```

정규화 규칙 (Fingerprint 계산 전용):
- 경로의 숫자 ID: `/\d+` → `/{id}`
- 경로의 UUID: `/[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}` → `/{uuid}`
- 경로의 긴 토큰: `/[A-Za-z0-9]{24,}` (경로 세그먼트 전체가 해당할 때만) → `/{token}`
- 캐시버스터 쿼리 파라미터 이름 제외: `_t`, `timestamp`, `_`, `cb`, `v`, `ver`
- 정규화된 경로 + 쿼리 키 이름 집합 = **Endpoint Signature**

#### Step 2: 요청 지문(Fingerprint) 생성

```
Fingerprint = SHA256(
    Method + "|" +
    Normalized_Path + "|" +
    Sorted_QueryParam_Names + "|" +     ← 값이 아닌 키 이름만 (캐시버스터 제외)
    Sorted_Body_Param_Names             ← POST body의 파라미터 이름만
)
```

#### Step 3: 오염 요청 처리 방침

**모든 요청은 기본적으로 재전송 대상이다.**
재전송으로 서비스에 이상이 발생한다면, 그 자체가 취약점/이슈로 볼 수 있으므로  
POST/PUT/DELETE, 파일 업로드 등 일체를 자동 제외하지 않는다.

단, 사용자가 대시보드에서 **제외할 엔드포인트 패턴을 직접 지정** 가능:

```
[대시보드 - 제외 엔드포인트 설정]
┌──────────────────────────────────────────────────┐
│  제외할 엔드포인트 (패턴 기반, 한 줄에 하나씩)    │
│    /api/auth/logout                              │
│    /api/payment/*                                │
│    POST:/api/user/delete                         │
│  [추가] [삭제] [초기화]                           │
└──────────────────────────────────────────────────┘
```

패턴 매칭 규칙:
- 경로만 입력 → 해당 경로 모든 Method 제외
- `METHOD:경로` 형식 → 해당 Method + 경로만 제외
- `*` 와일드카드 지원: `/api/payment/*`

#### Step 4: 정적 자원 필터

```java
// 기본 제외 확장자 (pdf 제외, map 포함)
Set<String> DEFAULT_STATIC_EXTENSIONS = Set.of(
    ".js", ".css", ".png", ".jpg", ".jpeg", ".gif", ".svg", ".ico",
    ".woff", ".woff2", ".ttf", ".eot", ".mp4", ".mp3", ".map"
    // .pdf 는 인가 체크 대상이므로 제외하지 않음
);

// 기본 제외 경로 프리픽스
Set<String> DEFAULT_STATIC_PATH_PREFIXES = Set.of(
    "/static/", "/assets/", "/resources/", "/public/", "/_next/", "/dist/"
);
```

대시보드에서 확장자 / 경로 프리픽스 모두 추가/삭제 가능.

#### Step 5: 중복 제거 알고리즘 (ON 상태)

```
pseudocode:

group_map = {}        // fingerprint → { representative, grouped_urls[] }
excluded_list = []    // 사용자 지정 제외 요청

for request in sitemap_requests:
    if is_static_resource(request):
        continue

    if matches_exclusion_pattern(request, user_exclusion_list):
        excluded_list.append(request)
        continue

    fp = compute_fingerprint(request)

    if fp not in group_map:
        group_map[fp] = { representative: request, grouped: [] }
    else:
        existing = group_map[fp].representative
        if has_more_params(request, existing):
            // 더 많은 파라미터의 요청을 대표로 승격, 기존은 grouped로
            group_map[fp].grouped.append(existing)
            group_map[fp].representative = request
        else:
            group_map[fp].grouped.append(request)

// 반환: 대표 요청 목록 + 그룹핑 메타데이터 (UI 드릴다운용)
return group_map, excluded_list
```

#### 중복 제거 OFF 모드

대시보드에서 **"중복 제거 비활성화"** 체크 시 → 정적 자원 필터와 제외 엔드포인트 필터만 적용하고,  
Fingerprint 기반 그룹핑 없이 모든 요청을 그대로 재전송한다.

---

### 3.2 MultiSessionReplayer - 다중 세션 재전송

#### Baseline 원칙: 모든 프로필 동시 재전송

> 사이트맵에 이미 캡처된 응답을 Baseline으로 쓰지 않는다.
> **User A를 포함한 모든 프로필을 동시에 재전송**하여 동일 시점의 응답을 수집한다.
> → 동적 컨텐츠(타임스탬프 등)로 인한 시간 차이 문제 제거
> → User A의 재전송 응답이 Baseline이 됨

```
Request #1: GET /api/user/123/profile

  동시 발송:
    ├── [User A 쿠키로 재전송] → Response A (신규 Baseline)
    └── [User B 쿠키로 재전송] → Response B

  비교: Response A (바디) vs Response B (바디)
```

#### 사용자 프로필 관리

```
[대시보드 - 사용자 프로필 관리]
┌──────────────────────────────────────────────────────────────────┐
│  프로필 목록                                [+ 프로필 추가]       │
├──────┬─────────────┬──────────────────────────────┬─────────────┤
│  #   │  이름       │  Cookie (미리보기)             │  Action     │
├──────┼─────────────┼──────────────────────────────┼─────────────┤
│  1   │  User A     │  session=AAA; token=xxx...    │  [편집][삭제]│
│  2   │  User B     │  session=BBB; token=yyy...    │  [편집][삭제]│
│  3   │  Admin      │  session=CCC; token=zzz...    │  [편집][삭제]│
└──────┴─────────────┴──────────────────────────────┴─────────────┘

[프로필 편집 다이얼로그]
  프로필 이름: [User A              ]
  Cookie 헤더: [session=AAA; token=xxx              ]
  커스텀 헤더 (선택):
    헤더 이름        │  헤더 값
    Authorization │  Bearer token_A
    X-User-Role   │  user
    [+ 헤더 추가]
```

#### 쿠키/헤더 교체 전략

```java
public HttpRequest replaceSession(HttpRequest original, UserProfile profile) {
    HttpRequest modified = original;

    // 1. 기존 Cookie 헤더 제거
    modified = modified.withRemovedHeader("Cookie");

    // 2. 프로필의 커스텀 헤더 목록에 있는 헤더들 제거 (교체 대상)
    for (String headerName : profile.getCustomHeaders().keySet()) {
        modified = modified.withRemovedHeader(headerName);
    }

    // 3. 프로필 Cookie로 교체
    if (profile.getCookieHeader() != null && !profile.getCookieHeader().isEmpty()) {
        modified = modified.withAddedHeader("Cookie", profile.getCookieHeader());
    }

    // 4. 프로필 커스텀 헤더 추가 (Authorization 등)
    for (Map.Entry<String, String> header : profile.getCustomHeaders().entrySet()) {
        modified = modified.withAddedHeader(header.getKey(), header.getValue());
    }

    return modified;
}
```

#### 병렬 처리 및 Rate Limit 설정 (대시보드에서 조정)

```
[대시보드 - 재전송 설정]
  Thread Pool 크기    : [10  ] (기본값)
  Rate Limit (req/s)  : [20  ] (기본값, 전체 발송량 기준)
      ※ 프로필 2개 × 20 req/s → 실제 서버에 40 req/s 도달
      ※ 서버 부하를 기준으로 설정할 것
  Timeout (초)        : [10  ] (기본값)
  프로필 간 딜레이 (ms): [0   ] (선택, 각 프로필 요청 사이 간격)
```

#### 진행 상황 관리

스캔 시작 후 결과 탭 상단에 진행 상태 표시:

```
[진행률 바]  123 / 450 요청 완료  (27%)  ●●●●●●●○○○○○○○○  [일시정지] [취소]

상태:
  완료: 123  |  오류: 2  |  남은 예상 시간: ~1분 32초
```

- **일시정지**: 현재 진행 중인 요청 완료 후 대기
- **취소**: 즉시 중단, 지금까지의 결과는 유지
- 스캔 완료 후 요약 토스트 메시지 표시

---

### 3.3 ResponseComparator - 응답 바디 비교 및 판정

**핵심 원칙:**
- **응답 헤더 제외** — `Date`, `X-Request-Time` 등 동적 값으로 해시 오염 방지
- **응답 바디만 비교**
- 판정: **SAME / SIMILAR / DIFFERENCE**
- 판정 결과는 정보 제공용 — **최종 판단은 사용자가 직접 한다**
- 결과 테이블에 상태코드를 별도 표시하여 판단 컨텍스트를 제공한다

#### 판정 기준

```
SAME:
  - User A 바디 해시 == User B 바디 해시  (정확 일치)
  - 또는 Content-Type 인식 정규화 후 해시 일치

SIMILAR:
  - 해시 불일치, 유사도 >= 임계값 (기본 80%, 대시보드 조정 가능)

DIFFERENCE:
  - 유사도 < 임계값
```

**주의:** SAME이 반드시 IDOR를 의미하지 않는다.
(예: 공개 엔드포인트, `/health` 등도 SAME으로 표시됨)  
결과 테이블의 상태코드와 URL 패턴을 함께 보고 판단해야 한다.

#### 응답 유사도 계산

```java
public ComparisonResult compare(byte[] bodyA, byte[] bodyB, String contentType) {
    // 1차: 해시 비교 (정확 매칭)
    if (sha256(bodyA).equals(sha256(bodyB))) {
        return ComparisonResult.SAME;
    }

    // 2차: Content-Type 인식 정규화
    String normalizedA = normalizeBody(new String(bodyA, UTF_8), contentType);
    String normalizedB = normalizeBody(new String(bodyB, UTF_8), contentType);

    if (sha256(normalizedA).equals(sha256(normalizedB))) {
        return ComparisonResult.SAME;
    }

    // 3차: 줄 단위 diff 유사도
    double similarity = computeLineSimilarity(normalizedA, normalizedB);

    return similarity >= similarityThreshold
        ? ComparisonResult.similar(similarity)
        : ComparisonResult.difference(similarity);
}
```

#### Content-Type 인식 정규화 (오탐 최소화)

기존 단순 regex 방식은 일반 텍스트를 잘못 치환하는 문제가 있음.  
Content-Type에 따라 정규화 전략을 분리한다:

```java
private String normalizeBody(String body, String contentType) {
    if (contentType.contains("application/json")) {
        return normalizeJson(body);
    } else if (contentType.contains("text/html")) {
        return normalizeHtml(body);
    } else {
        return body; // 그 외는 정규화 없이 그대로 비교
    }
}

// JSON 정규화: JSON 문자열 값 내에서만 패턴 치환
private String normalizeJson(String body) {
    // JSON 파싱 후 값(value)만 정규화 — 키(key)는 건드리지 않음
    // 타임스탬프 숫자 (10~13자리 Unix time)
    // UUID 형식 문자열
    // 명시적 토큰 키: "token", "csrf", "nonce", "sessionId" 등의 값
    // 그 외 긴 영숫자는 치환하지 않음 (상품명, 설명 텍스트 오탐 방지)
}

// HTML 정규화: form hidden input의 CSRF 토큰 값만 치환
private String normalizeHtml(String body) {
    return body.replaceAll(
        "(<input[^>]+name=[\"'](?:_token|csrf_token|authenticity_token)[\"'][^>]+value=[\"'])[^\"']*([\"'])",
        "$1{{CSRF}}$2"
    );
}
```

#### 결과 색상 코딩

```
SAME        → 빨강  (가장 의심 — 사용자 직접 확인 필요)
SIMILAR     → 주황  (의심)
DIFFERENCE  → 초록  (정상 가능성 높음)
```

---

## 4. UI 설계

### 4.1 Tab 구성

```
Burp Suite 탭:
└── IDOR/BOLA Checker
    ├── [설정] 탭
    │   ├── [사용자 프로필] 섹션
    │   │   ├── 프로필 목록 테이블 (이름 | Cookie 미리보기 | 커스텀 헤더 수)
    │   │   └── [+ 추가] [편집] [삭제]
    │   │
    │   ├── [재전송 설정] 섹션
    │   │   ├── Thread Pool 크기: [____]
    │   │   ├── Rate Limit (req/sec, 전체 기준): [____]
    │   │   ├── 프로필 간 딜레이 (ms): [____]
    │   │   └── Timeout (초): [____]
    │   │
    │   ├── [중복 제거 설정] 섹션
    │   │   ├── ☑ 중복 제거 활성화 (Fingerprint 기반)
    │   │   └── ※ 비활성화 시 모든 URL 개별 테스트
    │   │
    │   ├── [제외 엔드포인트] 섹션
    │   │   ├── 패턴 목록 (한 줄에 하나, 와일드카드 지원)
    │   │   └── [+ 추가] [삭제] [초기화]
    │   │
    │   ├── [정적 자원 필터] 섹션
    │   │   ├── 제외 확장자 (편집 가능 태그 UI)
    │   │   └── 제외 경로 프리픽스 (편집 가능 목록)
    │   │
    │   └── [비교 설정] 섹션
    │       └── 유사도 임계값 (SIMILAR 기준): [80]%
    │
    └── [결과] 탭
        ├── 진행률 바 (스캔 중에만 표시)
        │   └── "123 / 450 완료 (27%)  [일시정지] [취소]"
        │
        ├── 요약 바
        │   └── "총 450개 | SAME: 12 | SIMILAR: 34 | DIFFERENCE: 404"
        │
        ├── 결과 필터 바
        │   └── [전체] [SAME만] [SIMILAR만] [DIFFERENCE만]  검색: [________]
        │
        ├── 결과 테이블
        │   ├── # | Method | URL | 그룹수 | A 상태 | B 상태 | 판정 | 유사도(%)
        │   ├── 색상 코딩: 빨강(SAME) / 주황(SIMILAR) / 초록(DIFFERENCE)
        │   └── "그룹수" 컬럼: 중복 제거로 묶인 URL 수. 클릭 시 드릴다운
        │
        ├── 그룹 드릴다운 패널 (그룹수 클릭 시)
        │   └── 이 Fingerprint로 묶인 URL 목록
        │       예: /api/post/123, /api/post/456, /api/post/789
        │           (대표: /api/post/123)
        │
        ├── 하단 상세 패널 (결과 행 선택 시)
        │   ├── [User A 요청] [User A 응답 바디] 탭
        │   ├── [User B 요청] [User B 응답 바디] 탭
        │   └── [Diff 뷰] 탭 (A vs B 바디 diff 하이라이트)
        │
        └── [CSV 내보내기] 버튼
```

### 4.2 CSV 내보내기 형식

```csv
#,Method,URL,GroupCount,GroupedURLs,UserA_StatusCode,UserB_StatusCode,Verdict,Similarity
1,GET,https://example.com/api/user/123,3,"/api/user/456|/api/user/789",200,200,SAME,100.0
2,POST,https://example.com/api/post,1,"",200,403,DIFFERENCE,12.3
```

- `GroupedURLs`: 묶인 URL들을 `|` 구분자로 나열
- 스캔 진행 중에도 내보내기 가능 (현재까지 결과 기준)

### 4.3 Context Menu (우클릭 메뉴)

```
Sitemap 우클릭 시 표시:
└── Extensions
    └── IDOR/BOLA Checker
        ├── "선택된 호스트 전체 검사"
        ├── "선택된 경로 검사"
        └── "이 요청만 검사"
```

---

## 5. 프로젝트 구조

```
burp_extension_for_idor_and_bora_check/
├── build.gradle
├── settings.gradle
├── src/
│   └── main/
│       └── java/
│           └── com/security/idorchecker/
│               ├── IdorBoraBurpExtension.java
│               ├── menu/
│               │   └── SitemapContextMenuProvider.java
│               ├── core/
│               │   ├── RequestDeduplicator.java    # Fingerprint + 그룹핑 메타데이터 보존
│               │   ├── MultiSessionReplayer.java   # 모든 프로필 동시 재전송, Progress 이벤트
│               │   └── ResponseComparator.java     # Content-Type 인식 정규화, 바디만 비교
│               ├── model/
│               │   ├── UserProfile.java
│               │   ├── RequestGroup.java           # 대표 요청 + 묶인 URL 목록
│               │   ├── ScanResult.java             # 요청별 결과 (프로필별 응답 포함)
│               │   ├── ComparisonResult.java       # SAME/SIMILAR/DIFFERENCE + 유사도
│               │   └── ScanProgress.java           # 진행 상황 이벤트 모델
│               ├── config/
│               │   └── ExtensionSettings.java      # 전체 설정 직렬화/역직렬화
│               └── ui/
│                   ├── MainPanel.java
│                   ├── settings/
│                   │   ├── ProfileManagementPanel.java
│                   │   ├── ReplaySettingsPanel.java
│                   │   ├── DeduplicationSettingsPanel.java  # 중복 제거 ON/OFF + 임계값
│                   │   ├── ExclusionListPanel.java
│                   │   └── StaticFilterPanel.java
│                   └── results/
│                       ├── ResultPanel.java          # 진행률 바 포함
│                       ├── ResultTableModel.java
│                       ├── GroupDrilldownPanel.java  # 묶인 URL 드릴다운
│                       ├── DiffViewerPanel.java
│                       └── CsvExporter.java
└── DEVELOPMENT_PLAN.md
```

---

## 6. 개발 우선순위 및 단계

### Phase 1 - 핵심 기능 (MVP)
1. `RequestDeduplicator` — Fingerprint + 그룹핑 메타데이터 보존 (중복 제거 ON/OFF)
2. `UserProfile` + `ProfileManagementPanel` — 프로필 추가/삭제/편집
3. `MultiSessionReplayer` — 모든 프로필 동시 재전송 + Progress 이벤트
4. `ResponseComparator` — Content-Type 인식 정규화, 바디 해시 비교, SAME/SIMILAR/DIFFERENCE
5. `ResultPanel` — 실시간 진행률 바 + 결과 테이블 + 색상 코딩
6. `CsvExporter` — CSV 내보내기 (스캔 중/완료 후 모두 가능)
7. Context Menu (Sitemap 우클릭) 연동

### Phase 2 - 설정 고도화
1. `GroupDrilldownPanel` — 묶인 URL 목록 드릴다운 UI
2. `ExclusionListPanel` — 제외 엔드포인트 패턴 UI (와일드카드)
3. `StaticFilterPanel` — 필터 확장자/경로 커스터마이징 UI
4. `ReplaySettingsPanel` — Rate limit, Timeout, 프로필 간 딜레이
5. 결과 필터 바 (SAME/SIMILAR/DIFFERENCE 필터 + 키워드 검색)
6. 유사도 임계값 조정 UI
7. 3개 이상 다중 프로필 지원

### Phase 3 - 고급 기능
1. `DiffViewerPanel` — 바디 diff 하이라이트 뷰어
2. 자동 CSRF 토큰 재획득 처리 (세션별 토큰 선취득 후 교체)
3. 설정 Import/Export (다른 프로젝트에서 프로필 재사용)
4. Burp Scanner 통합 리포트

---

## 7. 주요 엣지케이스 처리

| 케이스 | 처리 방법 |
|--------|-----------|
| 응답 헤더의 동적 값 | 헤더 비교에서 제외, 바디만 해시 |
| 바디 내 동적 필드 | Content-Type 인식 정규화 (JSON/HTML 별도 처리) |
| 일반 텍스트가 토큰 regex에 오탐 | JSON 파싱 후 값만 정규화, 키/구조는 유지 |
| Redirect (301/302) | 최종 응답 바디까지 추적 |
| 대용량 응답 바디 | 처음 100KB만 유사도 계산, 전체 길이는 메타로 기록 |
| 빈 바디 | 양쪽 빈 바디 → SAME, 한쪽만 빈 바디 → DIFFERENCE |
| 쿠키 여러 개 | Cookie 헤더 전체 교체 (프로필 값 우선) |
| WebSocket | 제외 (HTTP/HTTPS만 대상) |
| CSRF 토큰 필요 요청 | Phase 3: 세션별 토큰 선취득 후 교체 |
| 스캔 중 네트워크 오류 | 오류 건은 별도 표시, 나머지 계속 진행 |
| 상태코드 차이 | 판정에는 미사용, 결과 테이블에 A/B 상태코드 각각 표시 → 사용자 판단 |
| 중복 제거 OFF + 대량 요청 | Rate limit 적용 그대로, 예상 완료 시간 표시 |

---

## 8. 설정 영속성

Extension 설정(프로필 목록, 제외 엔드포인트, 필터 설정, 재전송 설정, 유사도 임계값)은  
Burp Suite의 `IBurpExtenderCallbacks.saveExtensionSetting()` / `loadExtensionSetting()` 를 통해  
Burp 프로젝트 파일에 자동 저장/로드한다.

---

## 9. 빌드 및 설치

```bash
# 빌드
./gradlew shadowJar

# 결과물
build/libs/idor-bola-checker-all.jar

# Burp Suite 설치
Extensions > Add > 위 JAR 파일 선택
```
