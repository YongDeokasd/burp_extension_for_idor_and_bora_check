# IDOR/BOLA Checker — Burp Suite Extension

Burp Suite의 사이트맵·프록시 히스토리에 쌓인 요청을 **여러 사용자 세션(프로필)으로 동시에 재전송**하고, 응답을 비교해 IDOR / BOLA(Broken Object Level Authorization) 후보를 찾아주는 확장 프로그램입니다.

---

## 개요

권한 우회 점검은 보통 "사용자 A의 요청을 사용자 B의 세션으로 다시 보내고, 같은 데이터가 돌아오는지" 확인하는 작업의 반복입니다. 이 확장은 그 과정을 자동화합니다.

1. 점검에 쓸 사용자 세션을 **프로필**(Cookie + 커스텀 헤더)로 등록합니다. 예: `A — 피해자`, `B — 공격자`, `C — 관리자`
2. 사이트맵이나 HTTP 히스토리에서 요청을 골라 스캔을 시작합니다.
3. 각 요청을 **모든 프로필로 동시에 재전송**합니다. 기준이 되는 A도 함께 다시 보내므로, 오래전에 캡처된 응답이 아니라 같은 시점의 응답끼리 비교합니다.
4. 원하는 두 프로필을 골라 응답 **바디**를 비교하고 `SAME` / `SIMILAR` / `DIFFERENCE`로 판정합니다.
5. 같은 형태의 요청은 **중복 제거**로 묶어서 대표 하나만 보내므로, 큰 사이트맵도 빠르게 점검할 수 있습니다.

---

## 요구 사항

| 항목 | 버전 |
|---|---|
| Burp Suite | Professional 또는 Community (Montoya API 지원 버전) |
| Burp 실행 Java | 21 이상 (최신 Burp에 기본 포함) |
| 빌드용 JDK | 21 |
| HTTP history 단축키 `Ctrl+Shift+C` | Burp 2025.12 이상 (Montoya API 2025.12). 이전 버전에서는 이 단축키만 동작하지 않고 나머지 기능은 정상 동작 |

---

## JAR 빌드

저장소에 Gradle Wrapper가 포함되어 있어 Gradle을 따로 설치할 필요가 없습니다. JDK 21만 있으면 됩니다.

macOS / Linux:

```bash
./gradlew shadowJar
```

Windows:

```bash
gradlew.bat shadowJar
```

빌드 결과물:

```
build/libs/idor-bola-checker-1.0.0.jar
```

### Burp에 로드

1. Burp → **Extensions** → **Installed** → **Add**
2. Extension type: **Java**
3. Extension file: `build/libs/idor-bola-checker-1.0.0.jar` 선택 → **Next**
4. 상단에 **IDOR/BOLA** 탭이 생기면 완료. 로드 로그는 Extensions → 해당 확장 → **Output** 탭에서 확인합니다.

코드를 고친 뒤에는 다시 빌드하고, Extensions 목록에서 확장을 체크 해제 → 체크(또는 Remove 후 Add)하면 새 JAR가 반영됩니다.

---

## 주요 기능

- **사용자 프로필 관리**: 프로필마다 Cookie 헤더와 커스텀 헤더(Authorization, X-Role 등)를 지정
- **다중 프로필 동시 재전송**: 스레드 풀, 초당 요청 수 제한, 프로필 간 딜레이 설정
- **중복 제거 4단계**: Off / Low / Medium / High 중 선택
- **필터링**: OPTIONS 요청 항상 제외, 정적 리소스(확장자·경로 프리픽스) 제외, 사용자 정의 제외 엔드포인트
- **응답 비교**: 바디만 비교(헤더 제외), 유사도 임계값 설정. 매 요청마다 바뀌는 값은 비교 전에 정규화
  - JSON: UUID, 타임스탬프, 긴 숫자
  - HTML: hidden input 값(CSRF 토큰 등), csrf/xsrf meta 태그, script nonce. 나머지 페이지 내용은 그대로 비교
- **결과 대시보드**: 비교할 프로필 쌍 선택, 판정·메서드·상태코드·URL 필터, 요청/응답 보기, Diff 보기
- **결과 누적**: 새 스캔 결과가 기존 결과 뒤에 추가되고, 이미 점검한 엔드포인트는 다시 보내지 않음. `Clear`로 초기화
- **단축키**: HTTP history `Ctrl+Shift+C` → 스캔, 결과 화면 `Ctrl+R` → Repeater, `Ctrl+I` → Intruder
- **CSV 내보내기**: UTF-8(BOM 포함)으로 저장해 Excel에서 한글이 깨지지 않음
- **한글 등 비 ASCII 지원**: 응답 charset(헤더, HTML meta, UTF-8 자동 판별, EUC-KR) 기준으로 디코딩, 퍼센트 인코딩된 한글 URL도 글자로 표시

---

## 사용법

### 1. 프로필 등록 — Settings → User Profiles

1. **+ Add Profile**을 누르고 이름, Cookie 헤더 값, 필요한 커스텀 헤더를 입력합니다.
   - Cookie 예: `session=abc123; auth_token=eyJhb...`
   - 커스텀 헤더 예: `Authorization: Bearer eyJ...`
2. 등록 순서대로 `A`, `B`, `C` … 라벨이 붙습니다. 보통 A를 **피해자(리소스 소유자)**, B를 **공격자**로 둡니다.
3. 재전송 시 원래 요청의 Cookie 헤더는 프로필 값으로 **통째로 교체**되고, 커스텀 헤더는 같은 이름이 있으면 교체·없으면 추가됩니다.

> 프로필의 Cookie를 비워 두면 원래 요청에 있던 Cookie가 그대로 전송됩니다. 세션 없이(비로그인) 점검하려는 의도라면 이 점에 주의하세요.

### 2. 스캔 옵션 설정 — Settings

| 섹션 | 내용 |
|---|---|
| **Replay Settings** | Thread Pool Size, Rate Limit(req/s), Profile Delay(ms), Timeout(응답 대기 시간, 초). Rate Limit은 모든 프로필의 요청을 합친 **전체 초당 상한**이라, 프로필이 3개면 엔드포인트 처리 속도는 약 1/3이 됨 |
| **Dedup Settings** | 중복 제거 단계 선택 (아래 표) |
| **Exclusion Endpoints** | 재전송하지 않을 엔드포인트. 한 줄에 하나: `/api/auth/logout`(정확히 일치), `/api/payment/*`(프리픽스), `POST:/api/user/delete`(메서드 지정) |
| **Static Filter** | 제외할 확장자(.js, .css, .png, .map 등)와 경로 프리픽스(/static/, /assets/ 등). `.pdf`는 권한 점검 대상이라 기본적으로 제외하지 않음 |
| **Comparison** | SIMILAR 판정 임계값(%, 기본 80) |

**중복 제거 단계**

| 단계 | 같은 요청으로 묶는 기준 | 예시 |
|---|---|---|
| Off | 묶지 않음. 모든 요청을 개별 전송 | — |
| 1 — Low | 메서드와 URL(스킴·호스트·포트·경로·쿼리)이 **완전히 같은** 요청. 바디는 무시 | `/users/1?x=1` 두 번 → 1개 |
| 2 — Medium (기본) | 같은 엔드포인트 형태. 경로의 ID 값(숫자, UUID, 해시, `INV-8842` 같은 코드, 이메일)은 같은 것으로 보고, 쿼리·바디 **파라미터 이름**이 같아야 함 | `/users/1`, `/users/2` → 1개 |
| 3 — High | 경로 템플릿만 비교. 숫자가 들어간 경로 조각은 모두 ID로 보고, 파라미터는 무시. GraphQL은 operation별로 분리 | `/s?page=1`, `/s?sort=a` → 1개 |

모든 단계에서 메서드, 호스트, `/v1`·`/v2` 같은 API 버전은 다르면 별개로 취급합니다. 묶인 요청 수는 결과 표의 **Group** 열에 표시됩니다.

설정은 스캔을 시작할 때와 프로필을 바꿀 때 저장되며, Burp 프로젝트에 함께 보관됩니다.

### 3. 스캔 시작

| 위치 | 방법 |
|---|---|
| **Target → Site map** | 노드 우클릭 → `IDOR/BOLA Checker — Scan selected (N)` 또는 `Scan all site map requests for https://host` (해당 도메인의 사이트맵 요청 전체) |
| **Proxy → HTTP history** | 행 선택 후 `Ctrl+Shift+C`, 또는 우클릭 → `IDOR/BOLA Checker — Scan selected (N)` |
| 그 밖의 도구 (Repeater 등) | 요청 우클릭 → `IDOR/BOLA Checker — Scan selected (N)` |

스캔이 시작되면 **Results** 탭으로 이동합니다. 상단에 진행률과 함께 받은 요청 수, 제외된 수(OPTIONS·정적 리소스·제외 규칙), 이미 결과에 있어 건너뛴 수가 표시됩니다. `Pause` / `Resume` / `Cancel`로 진행을 제어할 수 있습니다.

### 4. 결과 보기 — Results

- **Compare Profiles**: 비교할 두 프로필을 드롭다운으로 고릅니다. 판정, 유사도, 상태 열, 요약 수치가 모두 이 쌍 기준으로 바뀝니다.
- **판정**

  | 판정 | 조건 |
  |---|---|
  | `SAME` | 바디 유사도 ≥ 99.9% |
  | `SIMILAR` | 유사도 ≥ 임계값(기본 80%) |
  | `DIFFERENCE` | 유사도 < 임계값 |

  판정은 **바디만** 기준입니다. 상태코드는 판정에 쓰지 않고 표에 함께 보여주므로, 둘을 같이 보고 판단하세요.
- **필터**: `All` / `SAME` / `SIMILAR` / `DIFFERENCE`, `Method`, 각 프로필의 상태코드 범위(`2xx`, `3xx`, `4xx`, `5xx`, `No response`), URL 검색(한글 검색 가능)
- **표 열**: `#`, Method, URL, Group(묶인 요청 수), 두 프로필의 상태코드, Verdict, Similarity
- **아래 상세 패널**
  - 프로필 탭(예: `A — user 1`): `Request` / `Response` 버튼으로 해당 프로필이 실제로 보낸 요청과 받은 응답을 전환
  - `Diff View`: 두 프로필 응답 바디의 줄 단위 차이

**판정 해석 팁**

- A(피해자) 리소스 요청에서 **B(공격자) 응답이 A와 `SAME`이고 상태가 2xx**면 IDOR/BOLA 의심 1순위입니다. `B Status: 2xx` 필터와 `SAME` 필터를 함께 쓰면 빠르게 추려집니다.
- 공개 페이지, 모두에게 같은 응답을 주는 API, 둘 다 같은 에러 페이지를 받은 경우(예: 403 vs 403)도 `SAME`이 나올 수 있으니 상태코드와 응답 내용을 반드시 확인하세요.

### 5. Repeater / Intruder로 보내기

결과 탭 어디서든(표, 요청/응답 보기, Diff View) 행을 선택하고:

| 단축키 | 동작 |
|---|---|
| `Ctrl+R` | 선택한 행을 Repeater로 전송. 탭 이름은 `A #12`처럼 프로필 라벨 + 행 번호 |
| `Ctrl+I` | 선택한 행을 Intruder로 전송 |

**아래에 열려 있는 프로필 탭**의 요청(그 프로필 세션이 적용된 요청)이 전송됩니다. Diff View가 열려 있으면 첫 번째 프로필 기준입니다. 여러 행을 선택하면 모두 전송됩니다.

### 6. 내보내기 · 초기화

- **Export CSV**: 현재 필터가 적용된 행을 CSV로 저장합니다. 열: `#`, Method, URL, Group, Fingerprint, 프로필별 상태코드, 선택한 쌍의 Verdict, Similarity
- **Clear**: 결과를 모두 지우고 번호를 1부터 다시 시작합니다. 실행 중인 스캔이 있으면 취소됩니다. Clear 전까지는 새 스캔 결과가 계속 뒤에 추가되고, 이미 결과에 있는 엔드포인트는 다시 보내지 않고 기존 행의 Group에 합쳐집니다.

---

## 제한 사항

- Timeout 설정은 Burp 2024.12(Montoya API 2024.12) 이상에서 적용됩니다. 그보다 오래된 Burp에서는 Burp 기본 네트워크 타임아웃이 쓰입니다.
- **응답 비교는 바디 앞 4,000자까지만** 사용합니다. 그 뒤에서만 달라지는 큰 응답은 `SAME`으로 보일 수 있습니다.
- 바디가 둘 다 비어 있으면 `SAME`(100%)으로 판정됩니다(예: 204 No Content).
- OPTIONS 요청은 설정과 관계없이 항상 재전송하지 않습니다.
- 엔드포인트 하나당 프로필 수만큼 요청이 나갑니다. 운영 환경에서는 Rate Limit을 낮게 잡고, 결제·삭제·로그아웃 같은 상태 변경 엔드포인트는 Exclusion Endpoints에 등록하세요.

---

## 프로젝트 구조

```
src/main/java/com/idorbola/checker/
├── IdorBolaExtension.java     # 확장 진입점 (탭·메뉴·단축키 등록)
├── SitemapContextMenu.java    # 우클릭 메뉴
├── HistoryHotKey.java         # HTTP history Ctrl+Shift+C
├── core/
│   ├── ScanEngine.java        # 필터링 → 중복 제거 → 프로필별 재전송 → 비교
│   ├── RequestDeduplicator.java
│   ├── ProfileReplacer.java   # Cookie/헤더 교체
│   └── ResponseComparator.java
├── model/                     # UserProfile, ScanConfig, DedupLevel, ScanResult ...
├── ui/                        # MainPanel, SettingsPanel, ResultsPanel ...
└── util/                      # CsvExporter, HttpText(charset 디코딩)
```

설계 배경은 `DEVELOPMENT_PLAN.md`, 초기 UI 시안은 `dashboard_demo.html`에 있습니다.
