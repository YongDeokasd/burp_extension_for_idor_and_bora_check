# Burp Suite IDOR/BOLA Checker Extension - 개발 계획서

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
┌─────────────────────────────────────────────────────────┐
│                    Burp Suite UI                         │
│  Sitemap 우클릭 → "IDOR/BOLA Check (Multi-Session)"      │
└──────────────────────┬──────────────────────────────────┘
                       │ Context Menu Action
                       ▼
┌─────────────────────────────────────────────────────────┐
│              SitemapContextMenuProvider                  │
│  - 선택된 호스트/경로의 모든 요청 수집                    │
└──────────────────────┬──────────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────────┐
│              RequestDeduplicator (핵심 모듈)             │
│  - Fingerprint 기반 중복 제거 (원본 URL 유지)             │
│  - 정적 자원 필터 (설정 가능)                            │
│  - 사용자 정의 제외 엔드포인트 필터                      │
└──────────────────────┬──────────────────────────────────┘
                       │ 정제된 요청 목록 (원본 URL 그대로)
                       ▼
┌─────────────────────────────────────────────────────────┐
│              MultiSessionReplayer                        │
│  - 프로필(User 1, User 2...) 기반 쿠키/헤더 교체         │
│  - ThreadPool 기반 병렬 재전송                           │
│  - 모든 요청 재전송 (제외 목록 외)                        │
└──────────────────────┬──────────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────────┐
│              ResponseComparator                          │
│  - 응답 바디만 해시 비교 (헤더 제외)                      │
│  - 바디 유사도 계산 (정규화 후 diff)                      │
│  - 판정: SAME / Similar / Difference                     │
└──────────────────────┬──────────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────────┐
│              ResultPanel (UI)                            │
│  - 결과 테이블 (URL / 프로필별 응답 / 판정)               │
│  - 의심 요청 강조 표시                                   │
│  - 상세 Diff 뷰어                                        │
└─────────────────────────────────────────────────────────┘
```

---

## 3. 핵심 모듈 상세 설계

### 3.1 RequestDeduplicator - 중복 패킷 필터링 알고리즘

**설계 원칙:**
- 정규화(Normalization)는 **중복 판별을 위한 Fingerprint 계산 전용**으로만 사용
- 실제 재전송 요청에는 **원본 URL과 파라미터 값을 그대로 사용**
  - 예: `?page=1&sort=asc` → 재전송 시 그대로 전달 (백엔드가 파라미터 존재 여부 자체를 인가 조건으로 사용할 수 있기 때문)

#### Step 1: Fingerprint용 URL 정규화 (재전송에는 미사용)

```
[Fingerprint 계산에만 사용 - 원본 URL은 변경하지 않음]

원본 URL: https://example.com/api/user/123?page=1&sort=asc&_t=1699999999
Fingerprint용 정규화: Method + "/api/user/{id}" + "page|sort"

재전송 URL: https://example.com/api/user/123?page=1&sort=asc&_t=1699999999  ← 그대로
```

**정규화 규칙 (Fingerprint 계산 전용):**
- 경로의 숫자 ID 치환: `/\d+` → `/{id}`
- 경로의 UUID 치환: `/[a-f0-9-]{36}` → `/{uuid}`
- 경로의 긴 토큰 치환: `/[A-Za-z0-9]{20,}` → `/{token}`
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

같은 Fingerprint → 동일 엔드포인트로 판단 → 대표 요청 1개만 선택  
대표 요청 선택 기준: 파라미터 수가 가장 많은 것 (가장 많은 정보 포함)

#### Step 3: 필터링 - 오염 요청 처리 방침

**모든 요청은 기본적으로 재전송 대상**이다.

재전송으로 서비스에 이상이 발생한다면, 그 자체가 취약점/이슈로 볼 수 있으므로  
POST/PUT/DELETE, 파일 업로드, 결제 요청 등 일체를 자동 제외하지 않는다.

대신 **사용자가 대시보드에서 직접 제외할 엔드포인트를 지정**할 수 있다:

```
[대시보드 - 제외 엔드포인트 설정]
┌──────────────────────────────────────────────────┐
│  제외할 엔드포인트 (패턴 기반, 한 줄에 하나씩)    │
│  예시:                                           │
│    /api/auth/logout                              │
│    /api/payment/*                                │
│    POST:/api/user/delete                         │
│                                                  │
│  [추가] [삭제] [초기화]                           │
└──────────────────────────────────────────────────┘
```

패턴 매칭 규칙:
- 경로만 입력 시: 해당 경로 모든 Method 제외
- `METHOD:경로` 형식: 해당 Method + 경로만 제외
- `*` 와일드카드 지원: `/api/payment/*`

#### Step 4: 정적 자원 필터

정적 자원은 인가 체크 대상이 아니므로 기본 제외. 단, **대시보드에서 완전히 커스터마이징 가능**.

**기본 제외 확장자** (사용자가 추가/삭제 가능):
```java
// 기본 정적 자원 확장자 (pdf 제외, map 포함)
Set<String> DEFAULT_STATIC_EXTENSIONS = Set.of(
    ".js", ".css", ".png", ".jpg", ".jpeg", ".gif", ".svg", ".ico",
    ".woff", ".woff2", ".ttf", ".eot", ".mp4", ".mp3", ".map"
    // .pdf 는 인가 체크 대상이므로 기본 필터에서 제외
);
```

**기본 제외 경로 프리픽스** (사용자가 추가/삭제 가능):
```java
Set<String> DEFAULT_STATIC_PATH_PREFIXES = Set.of(
    "/static/", "/assets/", "/resources/", "/public/", "/_next/", "/dist/"
);
```

**대시보드 - 필터 설정 UI:**
```
[정적 자원 필터 설정]
┌─────────────────────────────────────────────────────────┐
│  제외할 확장자:                                          │
│  .js .css .png .jpg .jpeg .gif .svg .ico .woff .woff2   │
│  .ttf .eot .mp4 .mp3 .map                               │
│  [확장자 추가: ______] [삭제]                            │
│                                                         │
│  제외할 경로 프리픽스:                                   │
│  /static/  /assets/  /resources/  /public/  /_next/     │
│  /dist/                                                 │
│  [경로 추가: ______] [삭제]                              │
└─────────────────────────────────────────────────────────┘
```

#### Step 5: 중복 제거 최종 알고리즘

```
pseudocode:

fingerprint_map = {}      // fingerprint → request (원본 URL 보존)
excluded_list = []        // 제외된 요청 (사용자 지정 엔드포인트)

for request in sitemap_requests:
    // Step 1: 정적 자원 필터 (확장자 + 경로 기반, 대시보드 설정 반영)
    if is_static_resource(request):
        continue
    
    // Step 2: 사용자 지정 제외 엔드포인트 필터
    if matches_exclusion_pattern(request, user_exclusion_list):
        excluded_list.append(request)
        continue
    
    // Step 3: Fingerprint 계산 (원본 URL은 변경 없음)
    fp = compute_fingerprint(request)  // 정규화는 여기서만 사용
    
    // Step 4: 중복 확인 - 파라미터 수 기반 대표 요청 선택
    if fp not in fingerprint_map:
        fingerprint_map[fp] = request  // 원본 요청 저장
    else:
        existing = fingerprint_map[fp]
        if has_more_params(request, existing):
            fingerprint_map[fp] = request  // 더 많은 파라미터의 원본 요청으로 교체

// 재전송 시 fingerprint_map의 값(원본 URL/파라미터 그대로) 사용
return list(fingerprint_map.values()), excluded_list
```

---

### 3.2 MultiSessionReplayer - 다중 세션 재전송

#### 사용자 프로필 관리

대시보드에서 User 프로필을 생성/편집/삭제하여 세션을 관리한다.

```
[대시보드 - 사용자 프로필 관리]
┌────────────────────────────────────────────────────────────────┐
│  프로필 목록                          [+ 프로필 추가]          │
├──────┬──────────────┬────────────────────────────┬────────────┤
│  #   │  이름        │  Cookie                    │  Action    │
├──────┼──────────────┼────────────────────────────┼────────────┤
│  1   │  User A      │  session=AAA; token=xxx     │  [편집][삭제]│
│  2   │  User B      │  session=BBB; token=yyy     │  [편집][삭제]│
│  3   │  User C(Admin)│ session=CCC; token=zzz    │  [편집][삭제]│
└──────┴──────────────┴────────────────────────────┴────────────┘

[프로필 편집 다이얼로그]
  프로필 이름: [User A              ]
  Cookie 헤더: [session=AAA; token=xxx              ]
  커스텀 헤더 (선택):
    헤더 이름          │  헤더 값
    Authorization  │  Bearer token_A
    X-User-Role    │  user
    [+ 헤더 추가]
```

#### 쿠키/헤더 교체 전략

```java
// 원본 요청의 인증 관련 헤더를 프로필 값으로 교체
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

#### 병렬 처리 설정 (대시보드에서 조정 가능)

```
Thread Pool Size = min(10, requests.size())  (기본값)
Rate Limiting    = 20 req/sec               (기본값, 대시보드 설정 가능)
Timeout          = 10초                     (기본값, 대시보드 설정 가능)
```

---

### 3.3 ResponseComparator - 응답 바디 비교 및 판정

**핵심 원칙:**
- **응답 헤더는 비교 대상에서 제외** — `Date`, `X-Request-Time` 등 동적 값 포함으로 해시 오염 발생
- **응답 바디만 비교** 대상으로 사용
- 판정 기준: **SAME / Similar / Difference** 3단계

#### 판정 기준

```
[판정 로직 - 응답 바디만 사용]

SAME:
  - 프로필 A 바디 해시 == 프로필 B 바디 해시
  - (완전 동일한 바디 → 권한 없는 세션도 동일 데이터 수신)

Similar:
  - 바디 해시는 다르지만 유사도 >= 설정 임계값 (기본 80%)
  - (동적 필드 차이는 있으나 구조/데이터 대부분 동일)

Difference:
  - 바디 유사도 < 설정 임계값
  - (응답 내용이 실질적으로 다름 → 정상 차단 또는 다른 응답)
```

유사도 임계값(기본 80%)은 대시보드에서 사용자가 조정 가능.

#### 응답 유사도 계산

```java
public ComparisonResult compare(byte[] bodyA, byte[] bodyB) {
    // 1차: 바디 해시 비교 (정확 매칭)
    String hashA = sha256(bodyA);
    String hashB = sha256(bodyB);
    
    if (hashA.equals(hashB)) {
        return ComparisonResult.SAME;
    }
    
    // 2차: 동적 데이터 정규화 후 유사도 계산
    String normalizedA = normalizeBody(new String(bodyA, StandardCharsets.UTF_8));
    String normalizedB = normalizeBody(new String(bodyB, StandardCharsets.UTF_8));
    
    // 정규화 후 재해시
    if (sha256(normalizedA).equals(sha256(normalizedB))) {
        return ComparisonResult.SAME;
    }
    
    // 3차: 줄 단위 diff 유사도
    double similarity = computeLineSimilarity(normalizedA, normalizedB);
    
    if (similarity >= similarityThreshold) {    // 기본 0.80
        return ComparisonResult.similar(similarity);
    } else {
        return ComparisonResult.difference(similarity);
    }
}

// 바디 동적 데이터 정규화 (타임스탬프, CSRF 토큰, 해시 등)
private String normalizeBody(String body) {
    return body
        .replaceAll("\\d{10,13}", "{{TIMESTAMP}}")              // Unix timestamp
        .replaceAll("[a-f0-9]{32,64}", "{{HASH}}")              // MD5/SHA hash
        .replaceAll("[A-Za-z0-9+/]{40,}={0,2}", "{{TOKEN}}")   // Base64 token
        .replaceAll("\"_token\"\\s*:\\s*\"[^\"]+\"",
                    "\"_token\":\"{{TOKEN}}\"")                 // CSRF token
        .replaceAll("\"nonce\"\\s*:\\s*\"[^\"]+\"",
                    "\"nonce\":\"{{NONCE}}\"");
}
```

#### 판정 결과 모델

```java
public enum Verdict { SAME, SIMILAR, DIFFERENCE }

public class ComparisonResult {
    private final Verdict verdict;
    private final double similarity;   // 0.0 ~ 1.0
    
    // 결과 테이블 색상 코딩
    // SAME        → 빨강  (가장 의심)
    // SIMILAR     → 주황  (의심)
    // DIFFERENCE  → 초록  (정상 가능성)
}
```

---

## 4. UI 설계

### 4.1 Tab 구성

```
Burp Suite 탭:
└── IDOR/BOLA Checker
    ├── [설정] 탭
    │   ├── [사용자 프로필] 섹션
    │   │   ├── 프로필 목록 테이블 (이름 | Cookie | 커스텀 헤더 수)
    │   │   └── [+ 추가] [편집] [삭제]
    │   │
    │   ├── [재전송 설정] 섹션
    │   │   ├── Thread Pool 크기: [____]
    │   │   ├── Rate Limit (req/sec): [____]
    │   │   └── Timeout (초): [____]
    │   │
    │   ├── [제외 엔드포인트] 섹션
    │   │   ├── 패턴 목록 (한 줄에 하나, 와일드카드 지원)
    │   │   └── [+ 추가] [삭제] [초기화]
    │   │
    │   └── [정적 자원 필터] 섹션
    │       ├── 제외 확장자 (편집 가능 태그 UI)
    │       └── 제외 경로 프리픽스 (편집 가능 목록)
    │
    └── [결과] 탭
        ├── 요약 바: 총 요청 수 | SAME: N | SIMILAR: N | DIFFERENCE: N
        ├── 결과 테이블
        │   ├── # | Method | URL | 프로필A 상태 | 프로필B 상태 | 판정 | 유사도(%)
        │   └── 색상: 빨강(SAME) / 주황(SIMILAR) / 초록(DIFFERENCE)
        └── 하단 상세 패널 (행 선택 시)
            ├── [원본 요청] [원본 응답 바디] 탭
            ├── [프로필A 요청] [프로필A 응답 바디] 탭
            ├── [프로필B 요청] [프로필B 응답 바디] 탭
            └── [Diff 뷰] 탭 (프로필 A vs B 바디 diff 하이라이트)
```

### 4.2 Context Menu (우클릭 메뉴)

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
│               ├── IdorBoraBurpExtension.java          # 진입점
│               ├── menu/
│               │   └── SitemapContextMenuProvider.java # 우클릭 메뉴
│               ├── core/
│               │   ├── RequestDeduplicator.java        # 중복 필터 (Fingerprint 전용 정규화)
│               │   ├── MultiSessionReplayer.java       # 프로필 기반 재전송
│               │   └── ResponseComparator.java         # 바디만 비교/판정
│               ├── model/
│               │   ├── UserProfile.java                # 사용자 프로필 (쿠키+커스텀 헤더)
│               │   ├── ScanResult.java                 # 스캔 결과
│               │   ├── ComparisonResult.java           # SAME/Similar/Difference 판정
│               │   └── RequestFingerprint.java         # 요청 지문 (중복 판별용)
│               ├── config/
│               │   └── ExtensionSettings.java          # 전체 설정 (직렬화/역직렬화)
│               └── ui/
│                   ├── MainPanel.java                  # 메인 탭
│                   ├── settings/
│                   │   ├── ProfileManagementPanel.java # 사용자 프로필 관리 UI
│                   │   ├── ReplaySettingsPanel.java    # 재전송 설정 UI
│                   │   ├── ExclusionListPanel.java     # 제외 엔드포인트 설정 UI
│                   │   └── StaticFilterPanel.java      # 정적 자원 필터 설정 UI
│                   └── results/
│                       ├── ResultPanel.java            # 결과 탭
│                       ├── ResultTableModel.java       # 결과 테이블 모델
│                       └── DiffViewerPanel.java        # Diff 뷰어
└── DEVELOPMENT_PLAN.md
```

---

## 6. 개발 우선순위 및 단계

### Phase 1 - 핵심 기능 (MVP)
1. `RequestDeduplicator` — Fingerprint 계산 + 중복 제거 (원본 URL 유지)
2. `UserProfile` 모델 + `ProfileManagementPanel` — 프로필 추가/삭제/편집
3. `MultiSessionReplayer` — 2개 프로필 쿠키 교체 후 재전송
4. `ResponseComparator` — 바디 해시 비교, SAME/Similar/Difference 판정
5. `ResultPanel` — 결과 테이블 + 색상 코딩
6. Context Menu (Sitemap 우클릭) 연동

### Phase 2 - 설정 고도화
1. `ExclusionListPanel` — 제외 엔드포인트 패턴 UI (와일드카드 지원)
2. `StaticFilterPanel` — 필터 확장자/경로 커스터마이징 UI
3. `ReplaySettingsPanel` — Rate limit, Timeout, Thread 수 설정
4. 바디 정규화 후 유사도 계산 (Levenshtein diff)
5. 유사도 임계값 조정 UI
6. 3개 이상 다중 프로필 지원

### Phase 3 - 고급 기능
1. `DiffViewerPanel` — 바디 diff 하이라이트 뷰어
2. 결과 내보내기 (JSON/CSV)
3. 자동 CSRF 토큰 재획득 처리
4. Burp Scanner 통합 리포트

---

## 7. 주요 엣지케이스 처리

| 케이스 | 처리 방법 |
|--------|-----------|
| 응답 헤더의 동적 값 | 비교 대상에서 헤더 제외, 바디만 해시 |
| 바디 내 동적 필드 | 정규화(타임스탬프, 해시, Base64 토큰 치환) 후 비교 |
| Redirect 응답 (301/302) | 최종 응답 바디까지 추적 |
| 대용량 응답 바디 | 처음 50KB만 유사도 계산, 전체 길이 비교 병행 |
| 쿠키 여러 개 포함 | Cookie 헤더 전체 교체 (프로필 값 우선) |
| WebSocket | 제외 (HTTP/HTTPS만 대상) |
| 빈 바디 응답 | 양쪽 모두 빈 바디 → SAME, 한쪽만 빈 바디 → DIFFERENCE |
| CSRF 토큰 필요 요청 | Phase 3에서 세션별 토큰 선취득 후 교체 |

---

## 8. 설정 영속성

Extension 설정(프로필 목록, 제외 엔드포인트, 필터 설정, 재전송 설정)은  
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
