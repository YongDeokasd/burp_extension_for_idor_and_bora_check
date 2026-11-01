# Burp Suite IDOR/BOLA Checker Extension - 개발 계획서

## 목적

Burp Suite 사이트맵(Sitemap)에 존재하는 모든 HTTP 요청에 대해 다수의 인증 세션(쿠키/헤더)을 교체하여 재전송하고,  
응답의 차이(Difference)를 비교함으로써 **IDOR(Insecure Direct Object Reference)** 및 **BOLA(Broken Object Level Authorization)** 취약점을 탐지합니다.

---

## 1. 기술 스택

| 항목 | 선택 |
|------|------|
| 언어 | Java 17+ |
| Burp API | Montoya API (burp-extender-api 2023.x+) |
| 빌드 | Gradle (Shadow JAR) |
| UI | Java Swing (Burp 기본 스타일) |
| 비교 알고리즘 | 응답 바디 해시 + 상태코드 + 길이 비교 |

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
│  - URL 정규화                                            │
│  - 중복 패킷 제거 알고리즘                               │
│  - 상태 변경 요청 격리                                   │
└──────────────────────┬──────────────────────────────────┘
                       │ 정제된 요청 목록
                       ▼
┌─────────────────────────────────────────────────────────┐
│              MultiSessionReplayer                        │
│  - 세션 A, B, C... 쿠키/헤더 교체                       │
│  - ThreadPool 기반 병렬 재전송                           │
│  - 원본 응답 포함 N+1개 응답 수집                        │
└──────────────────────┬──────────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────────┐
│              ResponseComparator                          │
│  - 상태코드 비교                                         │
│  - 응답 크기 비교                                        │
│  - 바디 유사도 계산 (Levenshtein / 해시)                 │
│  - IDOR/BOLA 판정 로직                                   │
└──────────────────────┬──────────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────────┐
│              ResultPanel (UI)                            │
│  - 결과 테이블 (요청 URL / 세션별 응답 / 판정 결과)       │
│  - 의심 요청 강조 표시                                   │
│  - 상세 diff 뷰어                                        │
└─────────────────────────────────────────────────────────┘
```

---

## 3. 핵심 모듈 상세 설계

### 3.1 RequestDeduplicator - 중복 패킷 필터링 알고리즘

중복/오염 패킷 제거가 이 Extension의 핵심입니다.

#### Step 1: URL 정규화 (Normalization)

```
원본 URL: https://example.com/api/user/123?page=1&sort=asc&_t=1699999999
정규화 후: https://example.com/api/user/{id}
```

- 경로 파라미터 패턴 감지: `/\d+/`, `/[a-f0-9-]{36}/` (UUID), `/[A-Za-z0-9]{20,}/`
- 쿼리 파라미터 중 캐시버스터 제거: `_t`, `timestamp`, `_`, `cb`, `v`, `ver`
- 정규화된 경로를 **Endpoint Signature**로 사용

#### Step 2: 요청 지문(Fingerprint) 생성

```
Fingerprint = SHA256(
    Method + "|" +
    Normalized_Path + "|" +
    Sorted_QueryParam_Names + "|" +     ← 값이 아닌 키 이름만
    Sorted_Body_Param_Names             ← POST body의 파라미터 이름만
)
```

같은 endpoint에 대한 요청은 동일한 Fingerprint를 가짐 → 중복 제거

#### Step 3: 오염 가능 요청(Poisonable Request) 분류 및 처리

```
[분류 기준]
1. 상태 변경 의심 요청:
   - Method: POST, PUT, PATCH, DELETE
   - 경로에 포함: /create, /update, /delete, /remove, /modify, /edit, /save, /logout

2. 파일 업로드 요청:
   - Content-Type: multipart/form-data

3. 결제/중요 트랜잭션:
   - 경로에 포함: /pay, /payment, /transfer, /purchase, /order/confirm

[처리 방법]
- 상태 변경 의심 → 사용자 확인 후 선택적 포함 (기본값: 제외)
- 파일 업로드 → 자동 제외
- 결제 관련 → 자동 제외 + 경고 표시
```

#### Step 4: 중복 제거 최종 알고리즘

```
pseudocode:

fingerprint_map = {}      // fingerprint → request
excluded_list = []        // 제외된 요청

for request in sitemap_requests:
    // Step 1: 오염 가능 요청 필터
    if is_poisonable(request):
        excluded_list.append(request)
        continue
    
    // Step 2: 정적 자원 필터
    if is_static_resource(request):
        continue    // JS, CSS, 이미지, 폰트 등 제외
    
    // Step 3: Fingerprint 계산
    fp = compute_fingerprint(request)
    
    // Step 4: 중복 확인 (우선순위 기반 대표 요청 선택)
    if fp not in fingerprint_map:
        fingerprint_map[fp] = request
    else:
        // 더 많은 파라미터를 가진 요청을 대표로 선택
        existing = fingerprint_map[fp]
        if has_more_params(request, existing):
            fingerprint_map[fp] = request

return list(fingerprint_map.values()), excluded_list
```

#### 정적 자원 필터 기준

```java
// 정적 자원 확장자
static Set<String> STATIC_EXTENSIONS = Set.of(
    ".js", ".css", ".png", ".jpg", ".jpeg", ".gif", ".svg", ".ico",
    ".woff", ".woff2", ".ttf", ".eot", ".mp4", ".mp3", ".pdf"
);

// 정적 자원 경로 패턴
static Set<String> STATIC_PATH_PREFIXES = Set.of(
    "/static/", "/assets/", "/resources/", "/public/", "/_next/", "/dist/"
);
```

---

### 3.2 MultiSessionReplayer - 다중 세션 재전송

#### 세션 구성

```
Session 설정 예시:
┌─────────────────────────────────────────────┐
│  세션 A (원본/피해자)                         │
│  Cookie: session=AAA; auth_token=xxx         │
│  Authorization: Bearer token_A               │
├─────────────────────────────────────────────┤
│  세션 B (공격자/다른 사용자)                  │
│  Cookie: session=BBB; auth_token=yyy         │
│  Authorization: Bearer token_B               │
├─────────────────────────────────────────────┤
│  세션 C (권한 없는 사용자 / 선택적)           │
│  Cookie: session=CCC                        │
└─────────────────────────────────────────────┘
```

#### 쿠키 교체 전략

```java
// 원본 요청의 쿠키를 세션 쿠키로 교체
public HttpRequest replaceSession(HttpRequest original, SessionConfig session) {
    HttpRequest modified = original;
    
    // 1. 기존 Cookie 헤더 제거
    modified = modified.withRemovedHeader("Cookie");
    modified = modified.withRemovedHeader("Authorization");
    
    // 2. 세션 설정의 쿠키로 교체
    if (session.getCookieHeader() != null) {
        modified = modified.withAddedHeader("Cookie", session.getCookieHeader());
    }
    
    // 3. 커스텀 헤더 추가 (Authorization 등)
    for (Map.Entry<String, String> header : session.getCustomHeaders().entrySet()) {
        modified = modified.withAddedHeader(header.getKey(), header.getValue());
    }
    
    return modified;
}
```

#### 병렬 처리 설정

```
Thread Pool Size = min(10, requests.size())
Rate Limiting = 20 req/sec (기본값, 사용자 설정 가능)
Timeout = 10초
```

---

### 3.3 ResponseComparator - 응답 비교 및 IDOR 판정

#### 판정 기준

```
[IDOR/BOLA 의심 판정 조건]

1. 완전 접근 허용 (HIGH):
   - 세션 A 응답 상태코드 = 200
   - 세션 B 응답 상태코드 = 200
   - 응답 바디 유사도 >= 80%
   
2. 부분 접근 (MEDIUM):
   - 세션 A 응답 상태코드 = 200
   - 세션 B 응답 상태코드 = 200
   - 응답 바디 유사도 50% ~ 79%
   
3. 정보 노출 (LOW):
   - 세션 A 상태코드 = 200, 세션 B 상태코드 = 200
   - 응답 길이 차이 < 10%

4. 정상 차단 (PASS):
   - 세션 B 응답 상태코드 = 401, 403, 404
```

#### 응답 유사도 계산

```java
public double calculateSimilarity(byte[] responseA, byte[] responseB) {
    // 1차: 해시 비교 (빠른 정확 매칭)
    if (Arrays.equals(hash(responseA), hash(responseB))) {
        return 1.0;
    }
    
    // 2차: 길이 비교
    double lengthRatio = (double) Math.min(responseA.length, responseB.length)
                        / Math.max(responseA.length, responseB.length);
    if (lengthRatio < 0.5) return lengthRatio;
    
    // 3차: 동적 데이터 정규화 후 비교
    String normalizedA = normalizeResponse(new String(responseA));
    String normalizedB = normalizeResponse(new String(responseB));
    
    // 4차: 줄 단위 diff 기반 유사도
    return computeLineSimilarity(normalizedA, normalizedB);
}

// 동적 데이터 정규화 (타임스탬프, CSRF 토큰 등 제거)
private String normalizeResponse(String body) {
    return body
        .replaceAll("\\d{10,13}", "TIMESTAMP")           // Unix timestamp
        .replaceAll("[a-f0-9]{32,}", "HASH")              // MD5/SHA hash
        .replaceAll("[A-Za-z0-9+/]{40,}={0,2}", "TOKEN") // Base64 token
        .replaceAll("\"_token\":\"[^\"]+\"", "\"_token\":\"TOKEN\"");
}
```

---

## 4. UI 설계

### 4.1 Tab 구성

```
Burp Suite 탭:
└── IDOR/BOLA Checker
    ├── [세션 설정] 탭
    │   ├── 세션 목록 (추가/삭제/편집)
    │   ├── 각 세션: 이름 | Cookie 헤더값 | 커스텀 헤더
    │   ├── 재전송 설정 (Rate limit, Timeout, Thread 수)
    │   └── 제외 설정 (오염 요청 처리 방식)
    │
    └── [결과] 탭
        ├── 결과 테이블
        │   ├── #  | URL | Method | 원본 Status | 세션A Status | 세션B Status | 판정 | 유사도
        │   └── 색상 코딩: 빨강(HIGH) / 주황(MEDIUM) / 노랑(LOW) / 초록(PASS)
        └── 하단 상세 패널
            ├── 원본 요청/응답 뷰어
            ├── 세션별 요청/응답 뷰어  
            └── Diff 뷰어
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
│               ├── IdorBoraBurpExtension.java      # 진입점, IBurpExtender
│               ├── menu/
│               │   └── SitemapContextMenuProvider.java  # 우클릭 메뉴
│               ├── core/
│               │   ├── RequestDeduplicator.java    # 핵심: 중복 필터링
│               │   ├── MultiSessionReplayer.java   # 다중 세션 재전송
│               │   └── ResponseComparator.java     # 응답 비교/판정
│               ├── model/
│               │   ├── SessionConfig.java          # 세션 설정 모델
│               │   ├── ScanResult.java             # 스캔 결과 모델
│               │   └── RequestFingerprint.java     # 요청 지문 모델
│               └── ui/
│                   ├── MainPanel.java              # 메인 탭 패널
│                   ├── SessionConfigPanel.java     # 세션 설정 UI
│                   └── ResultPanel.java            # 결과 표시 UI
└── DEVELOPMENT_PLAN.md
```

---

## 6. 개발 우선순위 및 단계

### Phase 1 - 핵심 기능 (MVP)
1. `RequestDeduplicator` - 중복 필터링 알고리즘 구현
2. `MultiSessionReplayer` - 2개 세션 쿠키 교체 후 재전송
3. `ResponseComparator` - 기본 상태코드/길이 비교
4. 기본 UI (세션 입력, 결과 테이블)
5. Context Menu (Sitemap 우클릭)

### Phase 2 - 고도화
1. 응답 바디 유사도 계산 (정규화 + diff)
2. 3개 이상 다중 세션 지원
3. 오염 요청 사용자 확인 UI
4. Rate limiting 설정
5. 결과 내보내기 (JSON/CSV)

### Phase 3 - 고급 기능
1. 자동 CSRF 토큰 재사용 처리
2. 경로 파라미터 자동 감지 및 치환 값 제안
3. Diff 뷰어 (하이라이트)
4. Burp Scanner와 통합 리포트

---

## 7. 주요 엣지케이스 처리

| 케이스 | 처리 방법 |
|--------|-----------|
| CSRF 토큰 필요 요청 | 세션별로 먼저 토큰 획득 후 교체 (고급 기능) |
| JSON 바디 동적 필드 | 타임스탬프/nonce 자동 정규화 |
| Redirect 응답 (301/302) | 최종 응답까지 추적 |
| 대용량 응답 | 처음 10KB만 비교, 나머지 길이 비교 |
| 쿠키 여러 개 | 이름 기준으로 교체, 나머지 유지 또는 전체 교체 선택 |
| WebSocket | 제외 (HTTP만 대상) |

---

## 8. 빌드 및 설치

```bash
# 빌드
./gradlew shadowJar

# 결과물
build/libs/idor-bola-checker-all.jar

# Burp Suite 설치
Extensions > Add > 위 JAR 파일 선택
```
