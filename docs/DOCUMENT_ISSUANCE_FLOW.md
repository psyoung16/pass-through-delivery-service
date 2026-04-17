# 서류 발급 플로우

## 전체 흐름도

```
[사용자]
   |
   | 서류 발급 요청
   v
[DocumentRequested]
   |
   +-- issuanceMethod: USER_UPLOADED
   |     |
   |     | 사용자가 파일 첨부
   |     v
   |  [DocumentUploaded]
   |     |
   |     v
   |  [COMPLETED]
   |
   +-- issuanceMethod: API_ISSUED
         |
         | 외부 기관 API 호출
         v
      [처리 중...]
         |
         +-- 성공
         |     |
         |     v
         |  [DocumentIssued]
         |     |
         |     v
         |  [COMPLETED]
         |
         +-- 2차 인증 필요
               |
               v
            [TwoWayAuthRequired]
               |
               v
            [TWO_WAY_AUTH_REQUIRED]
               |
               | 사용자 2차 인증 완료 (외부에서)
               | 재시도
               v
            [처리 중...]
               |
               +-- 성공
               |     |
               |     v
               |  [DocumentIssued]
               |     |
               |     v
               |  [COMPLETED]
               |
               +-- 실패
                     |
                     v
                  [DocumentIssueFailed]
                     |
                     v
                  [FAILED]
```

## 발급 방식별 플로우

### 1. 사용자 직접 업로드 (USER_UPLOADED)

**사용자 액션:**
1. 서류 발급 요청 (최초 1회)
2. 파일 첨부

**이벤트 흐름:**
```
DocumentRequested (상태: REQUESTED)
  -> DocumentUploaded (상태: COMPLETED)
```

**특징:**
- 사용자가 직접 서류를 준비하여 업로드
- 외부 API 호출 없음
- 2차 인증 불필요

---

### 2. API 자동 발급 (API_ISSUED)

**사용자 액션:**
1. 서류 발급 요청 (최초 1회)
2. (2차 인증 필요 시) 외부 인증 사이트에서 인증 완료

**이벤트 흐름 - 정상:**
```
DocumentRequested (상태: REQUESTED)
  -> ProcessingStarted (상태: PROCESSING)
  -> DocumentIssued (상태: COMPLETED)
```

**이벤트 흐름 - 2차 인증 필요:**
```
DocumentRequested (상태: REQUESTED)
  -> ProcessingStarted (상태: PROCESSING)
  -> TwoWayAuthRequired (상태: TWO_WAY_AUTH_REQUIRED)

  [사용자가 외부에서 2차 인증 완료]

  -> ProcessingRetried (상태: PROCESSING)
  -> DocumentIssued (상태: COMPLETED)
```

**이벤트 흐름 - 실패:**
```
DocumentRequested (상태: REQUESTED)
  -> ProcessingStarted (상태: PROCESSING)
  -> DocumentIssueFailed (상태: FAILED)
```

**또는:**
```
DocumentRequested (상태: REQUESTED)
  -> ProcessingStarted (상태: PROCESSING)
  -> TwoWayAuthRequired (상태: TWO_WAY_AUTH_REQUIRED)

  [사용자가 외부에서 2차 인증 완료]

  -> ProcessingRetried (상태: PROCESSING)
  -> DocumentIssueFailed (상태: FAILED)
```

---

## 사용자 관점 vs 시스템 관점

### 사용자 관점
- **서류 발급 요청** (1회만 함)
- (필요 시) 외부 사이트에서 본인 인증
- 결과 확인 (완료 또는 실패)

### 시스템 관점
- DocumentRequested (요청 접수)
- ProcessingStarted (외부 API 호출 시작)
- TwoWayAuthRequired (2차 인증 필요 감지)
- ProcessingRetried (2차 인증 완료 후 재시도)
- DocumentIssued 또는 DocumentIssueFailed (최종 결과)

---

## 주요 상태 (DocumentStatus)

| 상태 | 설명 | 사용자에게 보이는 메시지 |
|------|------|------------------------|
| REQUESTED | 요청 접수됨 | "서류 발급을 요청했습니다" |
| PROCESSING | 외부 기관에 발급 요청 중 | "서류를 발급하고 있습니다" |
| TWO_WAY_AUTH_REQUIRED | 2차 인증 필요 | "본인 인증이 필요합니다" |
| COMPLETED | 발급 완료 | "서류 발급이 완료되었습니다" |
| FAILED | 발급 실패 | "서류 발급에 실패했습니다" |

---

## 외부 기관 API 비동기 처리

### 문제 상황
외부 기관 API는 동기 응답 방식이지만, **성공 시 응답을 물고 있는(hanging) 특성**이 있습니다.

**외부 기관 응답 패턴:**
- ✅ **2차 인증 필요**: 즉시 응답 (200ms 이내)
- ✅ **발급 실패**: 즉시 응답 (200ms 이내)
- ❌ **발급 성공**: 응답 없음 (무한 대기 또는 매우 긴 대기)

### 해결 방법: 타임아웃 기반 성공 판단

**비동기 처리 전략:**
```
외부 API 호출 시작
   |
   +-- 2-3초 이내 응답 없음 → 성공으로 간주 → DocumentIssued
   |
   +-- 즉시 "2차 인증 필요" 응답 → TwoWayAuthRequired
   |
   +-- 즉시 "실패" 응답 → DocumentIssueFailed
```

**타임아웃 기반 플로우:**
```
DocumentRequested (상태: REQUESTED)
  -> ProcessingStarted (상태: PROCESSING)
  -> [외부 API 호출]
     |
     +-- 2-3초 타임아웃 → DocumentIssued (상태: COMPLETED)
     |
     +-- 즉시 응답(2차 인증) → TwoWayAuthRequired (상태: TWO_WAY_AUTH_REQUIRED)
     |
     +-- 즉시 응답(실패) → DocumentIssueFailed (상태: FAILED)
```

**구현 고려사항:**
- 타임아웃: 2-3초 (외부 기관 특성에 맞게 조정)
- 타임아웃 시 = 성공으로 간주
- 즉시 응답 = 2차 인증 필요 또는 실패

**레거시 문제점:**
- 동기 방식으로 무한 대기 → 시스템 리소스 낭비
- 성공/실패 판단 불가능
- 사용자 경험 저하 (언제 완료될지 모름)

**개선안 (EDA):**
- 비동기 처리로 즉시 응답
- 타임아웃 기반 성공 판단
- 이벤트로 상태 추적 가능

### 비동기 처리 전략

**멀티스레딩의 한계:**
- Thread Pool 고갈 위험 (Tomcat 기본: 200개)
- 메모리 부족 (각 스레드 약 1MB)
- 동시 요청 급증 시 서버 다운 가능성
- 컨텍스트 스위칭 오버헤드

**구현 방안:**

**1. 이벤트 기반 비동기 처리 (추후 고려)**
```
사용자 요청 → DocumentRequested 발행 (즉시 응답)
  → 비동기 Worker가 외부 API 호출 (별도 프로세스)
  → 결과에 따라 이벤트 발행
     - DocumentIssued
     - TwoWayAuthRequired
     - DocumentIssueFailed
```

**장점:**
- 사용자 요청 즉시 응답 (202 Accepted)
- Thread Pool 블로킹 없음
- 메시지 큐(RabbitMQ, Kafka)로 확장 가능
- Worker를 독립적으로 스케일 아웃 가능

**2. 타임아웃 + 백프레셔 (현재 채택)**
```kotlin
@Async
@Timeout(3000) // 3초 타임아웃
fun callExternalApi() {
    // 외부 API 호출
}
```

**구현 전략:**
- 동시 처리 제한 (예: 최대 50개)
- 초과 요청은 대기열에 추가
- 타임아웃으로 스레드 블로킹 시간 제한
- Thread Pool 크기 조정

**선택 이유:**
- 구현 복잡도 낮음
- 현재 트래픽 규모에 적합
- 추후 메시지 큐 기반으로 전환 가능

---

## 2차 인증 관련 용어 정리

**문제:**
- "API 발급 요청 시 2차 인증이 필요하면" → 시스템 관점 용어
- "서류 발급을 요청하면" → 이미 사용 중 (최초 요청)

**고려 사항:**
- 사용자는 "서류 발급 요청"을 한 번만 함
- 2차 인증은 **시스템이 발급 처리 중** 감지하는 것
- 재시도는 **시스템이 자동**으로 수행

**제안:**
1. "서류 발급 처리 중 2차 인증이 필요하면"
2. "외부 기관에서 2차 인증을 요구하면"
3. "발급 중 본인 인증이 필요하면"

→ **"외부 기관에서 2차 인증을 요구하면"** 이 가장 명확할 것 같음