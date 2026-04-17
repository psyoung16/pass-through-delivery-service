# 기존 설계의 문제점과 EDA 전환 이유

## 기존 설계 (레거시)

### 아키텍처 계층

**외부 API 호출 구조:**
- **1차**: 대법원 등 공공기관 (동기 API)
- **2차**: Codef 중계 서비스 (동기 처리로 추정)
- **3차**: 레거시 시스템 (동기 처리)

**Codef 응답 패턴:**
- 성공 시: 응답을 오래 물고 있음 (200ms~ 이상)
- 실패/2차 인증: 즉시 응답 (200ms 이내)

### 아키텍처 개요

**단일 엔티티 중심 설계**
- 하나의 엔티티에 모든 책임 집중
- 요청/응답 데이터, 상태 관리, 2-way 인증 정보, 1차/2차 요청 연결

```
CodefApiRequest (단일 테이블)
├── 요청 정보 (requestData, apiType, apiEndpoint)
├── 응답 정보 (responseData, responseCode, responseMessage)
├── 상태 관리 (status: PENDING/SUCCESS/FAILED/TWO_WAY_REQUIRED)
├── 2-way 인증 (transactionId, jti, twoWayInfo)
├── 1차/2차 연결 (parentRequest)
└── 동시성 제어 (@Version - 낙관적 락)
```

### 주요 문제점

#### 1. 히스토리 추적 불가능

**문제:**
```
PENDING → TWO_WAY_REQUIRED → SUCCESS
```
- 상태가 변경되면 이전 상태는 사라짐
- "언제, 왜" 상태가 변경됐는지 추적 불가
- 재시도 이력, 실패 원인 변화 등을 알 수 없음

**현실 시나리오:**
```
사용자: "왜 서류 발급이 실패했나요?"
운영자: 현재 상태만 보임 (FAILED)
       → 몇 번 재시도했는지?
       → 처음 실패 원인이 뭐였는지?
       → 2-way 인증 단계에서 문제였는지?
       알 수 없음
```

#### 2. 복잡한 parent-child 관계

**문제:**
- 1차 요청과 2차 요청을 `parentRequest` FK로 연결
- 2-way 인증 플로우를 추적하려면 JOIN 필요
- N차 재시도 시 parent 체인이 길어짐

**쿼리 복잡도:**
```sql
-- 전체 플로우를 보려면 재귀 쿼리 필요
WITH RECURSIVE request_chain AS (
  SELECT * FROM codef_api_request WHERE id = ?
  UNION ALL
  SELECT r.* FROM codef_api_request r
  JOIN request_chain rc ON r.parent_id = rc.id
)
SELECT * FROM request_chain;
```

#### 3. 낙관적 락(@Version)의 한계

**문제:**
```java
@Version
private Long version;
```
- 동시에 상태 업데이트 시 version 충돌
- 충돌 발생 시 재시도 로직 필요
- 분산 환경에서 복잡도 증가

**실제 충돌 시나리오:**
```
Thread 1: 2-way 인증 응답 처리 → version 1→2
Thread 2: 타임아웃 처리 → version 1→2 (충돌!)
```

#### 4. 로그와 비즈니스 로직의 혼재

**코드 주석:**
> "내용은 log지만 해당 db를 이용하여 비즈니스로직을 처리"

- 원래는 API 호출 로그 테이블
- 점차 비즈니스 로직이 추가됨
- 책임 불명확

#### 5. 동기 처리로 인한 성능 문제

**레거시 처리 방식:**
```
사용자 요청
  ↓
[서버 Thread가 Codef API 호출하면서 blocking]
  ↓
Codef 응답 대기 (성공 시 200ms~ 이상)
  ↓
사용자에게 응답
```

**문제:**
- **Thread Pool 고갈 위험**: Tomcat 기본 Thread Pool 200개
  - 동시 요청 200개 초과 시 대기 또는 거부
  - 각 요청이 오래 blocking되면 가용 Thread 부족
- **사용자 대기 시간 증가**: 외부 API 응답을 기다리는 동안 사용자도 대기
- **시스템 확장성 저하**: Thread 수를 늘려도 근본적 해결 안 됨

**Codef도 동기 처리:**
- 2차 중계 서비스(Codef)도 동기 방식으로 대법원 API 호출
- Codef의 한계를 그대로 답습하게 됨

#### 6. 상태 기반 플로우의 한계

**상태 전이:**
```
PENDING → PROCESSING → SUCCESS/FAILED/TWO_WAY_REQUIRED
```

**문제:**
- 복잡한 플로우를 상태로만 표현하기 어려움
- 예: "2-way 인증 1차 성공 → 2차 대기 → 2차 실패 → 재시도"
  → 어느 상태로 표현?

## EDA 전환 후 개선점

### 1. 완전한 히스토리 추적

**이벤트 소싱:**
```
DocumentRequested(t=0)
→ TwoWayAuthRequired(t=10s)
→ DocumentIssueFailed(t=20s, reason="본인인증 실패")
→ DocumentResubmitted(t=30s)
→ DocumentRequested(t=31s)
→ DocumentIssued(t=50s)
```

**장점:**
- 전체 과정을 시간순으로 완전 추적
- 실패 원인의 변화 추적 가능
- 운영자가 전체 컨텍스트 파악

### 2. 도메인 분리

**Consent와 Document 완전 분리:**
```
Consent (동의서)
- 상태: DRAFT → SUBMITTED
- Document 상태와 무관

Document (첨부서류)
- 독립적인 라이프사이클
- 이벤트로만 통신
```

**장점:**
- 각 도메인의 책임 명확
- 첨부서류 실패가 동의서 제출을 막지 않음 (Pass-Through)

### 3. 이벤트 기반 플로우

**복잡한 2-way 인증도 명확하게:**
```
ConsentSubmitted
→ DocumentRequested
→ TwoWayAuthRequired
→ UserAuthenticated
→ DocumentIssued
```

**장점:**
- 각 단계가 이벤트로 명확히 표현
- 플로우 추적 용이
- 새로운 단계 추가도 쉬움

### 4. 동시성 제어 개선

**이벤트 순서로 제어:**
- 낙관적 락 대신 이벤트 순서 보장
- 이벤트 스트림에서 순서 관리
- 충돌 대신 순차 처리

### 5. 비동기 처리 도입

**핵심 개선: 2차(Codef)가 하지 않은 비동기 처리를 3차에서 구현**

**개선된 처리 방식:**
```
사용자 요청
  ↓
즉시 "접수됨" 응답 (202 Accepted)
  ↓
[백그라운드 Worker가 Codef API 호출]
  ↓
사용자는 나중에 결과 조회
```

**장점:**
- **Thread Pool 효율화**: 사용자 요청은 즉시 처리, 백그라운드에서 외부 API 호출
- **사용자 경험 개선**: 즉시 응답, 긴 대기 시간 없음
- **시스템 확장성**: 외부 API 지연이 전체 시스템에 영향 주지 않음
- **Pass-Through 구현**: 첨부서류 발급 실패가 동의서 제출을 막지 않음

**구현 전략:**
- 사용자 요청 → DocumentRequested 이벤트 발행 (즉시 응답)
- 비동기 Worker가 이벤트를 소비하여 외부 API 호출
- 결과를 DocumentIssued/DocumentIssueFailed 이벤트로 발행
- 타임아웃 기반 성공 판단 (2-3초)

### 6. 운영 효율화

**기존 (데이터 중심):**
- 실패 건 찾기: `SELECT * WHERE status = 'FAILED'` (가능)
- 재시도 이력: 데이터는 남겼지만, 복잡한 쿼리 필요
  ```sql
  -- parent-child 체인을 따라 전체 이력 조회
  WITH RECURSIVE history AS (...)
  ```
- 문제: **"왜 실패했는가"**, **"몇 번째 재시도인가"**를 파악하려면 여러 레코드를 직접 분석
- 상태만 보면 맥락을 알 수 없음

**EDA (이벤트 중심):**
- 실패 건: `DocumentIssueFailed` 이벤트 조회
- 재시도 이력: 이벤트 스트림을 시간순으로 읽으면 전체 스토리 파악
  ```
  [t=0] DocumentRequested
  [t=10] DocumentIssueFailed (reason: "본인인증 실패")
  [t=30] DocumentResubmitted
  [t=31] TwoWayAuthRequired
  [t=50] UserAuthenticated
  [t=60] DocumentIssued
  ```
- 장점: 각 단계의 **맥락**(왜, 언제, 어떻게)이 이벤트에 포함

## 결론

**레거시 → EDA 전환 이유:**
1. **완전한 히스토리 추적**: 운영 이슈 분석 가능
2. **도메인 분리**: Pass-Through 아키텍처 구현
3. **비동기 처리**: Codef가 하지 않은 개선을 3차에서 구현
4. **확장 용이성**: 새로운 플로우 추가 쉬움
5. **운영 효율화**: 실패 건 재처리 업무 간소화

**핵심 가치:**
> "로그가 비즈니스 로직이 되는" 안티패턴 → "이벤트가 진실의 원천(Event as Source of Truth)"
>
> "동기 처리로 Thread Pool 고갈" → "비동기 처리로 즉시 응답 및 확장성 확보"