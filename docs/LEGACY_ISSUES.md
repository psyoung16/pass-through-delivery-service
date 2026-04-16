# 기존 설계의 문제점과 EDA 전환 이유

## 기존 설계 (레거시)

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

#### 5. 상태 기반 플로우의 한계

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

### 5. 운영 효율화

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
3. **확장 용이성**: 새로운 플로우 추가 쉬움
4. **운영 효율화**: 실패 건 재처리 업무 간소화

**핵심 가치:**
> "로그가 비즈니스 로직이 되는" 안티패턴 → "이벤트가 진실의 원천(Event as Source of Truth)"