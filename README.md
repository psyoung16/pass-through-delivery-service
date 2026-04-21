# Pass-Through Delivery Service

재개발/재건축 동의서 제출 시 필요한 **첨부서류 발급 히스토리를 이벤트 기반으로 추적**하는 비동기 처리 시스템입니다.

## 핵심 문제

재개발/재건축 동의서 제출 시 첨부서류 제출 방식:

**1. 외부 API 자동 발급** (간편발급)
- **2-way 인증 플로우**: 1차 요청 → 외부 인증(카카오, 금융기관) → 2차 요청
- 본인인증 후 실제 서류 발급
- **서류 타입별 외부 API 호출**:
  - 주민등록등본 → 정부24 API
  - 가족관계증명서 → 대법원 API
- **3초 이상 소요** (실제 운영: 10초~3분)

### 문제점 (API 자동 발급)

- 동기 처리 시: 사용자가 2-3분 대기, 여러 서류 발급 시 4-6분
- 2-way 인증 실패 시 재인증 필요
- 외부 API 장애 시 동의서 제출 자체가 실패
- **동의서 제출 프로세스에 병목 발생**

**2. 사용자 직접 업로드**
- 사진 찍어서 즉시 제출 (검증은 운영팀)

## 해결 방안

**Pass-Through 비동기 파이프라인**
- 동의서 제출과 첨부서류 발급을 분리하여 독립적으로 처리
- 사용자에게 즉시 응답 (202 ACCEPTED), 백그라운드에서 처리
- 외부 API 장애 시에도 동의서 접수는 완료
- 첨부서류는 백그라운드에서 병렬 발급
- **사용자는 폴링하지 않음**: "처리되었습니다" 응답만 받고 종료
- **실패 시**: 운영진이 나중에 확인하고 재처리

**이벤트 기반 히스토리 추적**
- 첨부서류 발급의 전체 라이프사이클을 이벤트로 기록
- `요청 → 실패 → 재제출 → 완료` 과정을 완전히 추적
- 운영자의 실패 건 재처리 업무 효율화

**서류 타입별 외부 API 클라이언트**
- Factory 패턴으로 DocumentType에 따라 적절한 API 클라이언트 선택
- `Gov24ApiClient`: 주민등록등본 발급 (정부24 API)
- `CourtApiClient`: 가족관계증명서 발급 (대법원 API)
- 각 서류 타입마다 다른 외부 API 호출

## 핵심 기술

- **이벤트 소싱**: 첨부서류 발급 히스토리 완전 추적
- **비동기 메시징**: Message Queue 기반 첨부서류 병렬 처리
- **Saga 패턴**: 동의서 제출과 첨부서류 발급의 분산 트랜잭션 관리

## 기술 스택

- **Language**: Kotlin 2.3.20
- **Framework**: Spring Boot 4.1.0-SNAPSHOT
- **ORM**: Spring Data JPA
- **Build Tool**: Gradle (Kotlin DSL)
- **Java Version**: 25

## 주요 기능 (예정)
- [ ] 동의서 제출과 첨부서류 발급을 독립적으로 처리하는 비동기 파이프라인
- [ ] 외부 API 연동 (정부24, 대법원)
  - 주민등록등본 자동 발급
  - 가족관계증명서 자동 발급
  - 2-way 인증 플로우 처리 (1차 요청 → 본인인증 → 2차 요청)
- [ ] 첨부서류 발급 이벤트 소싱
  - `요청 → 2-way 인증 → 실패 → 재제출 → 완료` 전체 히스토리 추적
- [ ] 운영자의 발급 실패 건 재처리 기능

## API 설계

### REST API 엔드포인트

```
POST   /api/documents/upload                # 사용자 직접 업로드 (원샷)
POST   /api/documents/issuance              # API 자동 발급 요청 (2-way 인증 시작)
POST   /api/documents/{id}/complete         # 2-way 인증 완료 후 실제 발급
GET    /api/documents/{id}                  # 서류 상태 조회
```

### 프론트엔드 사용 예시

#### 1. 사용자 직접 업로드
```javascript
const response = await fetch('/api/documents/upload', {
  method: 'POST',
  body: JSON.stringify({
    consentId: 100,
    memberId: 200,
    documentType: 'RESIDENT_REGISTRATION',
    fileUrl: 'https://s3.../document.pdf'
  })
})
// → 즉시 완료, 추가 작업 불필요
```

#### 2. API 자동 발급 (2-way 인증)
```javascript
// Step 1: 간편인증 요청
const res1 = await fetch('/api/documents/issuance', {
  method: 'POST',
  body: JSON.stringify({
    consentId: 100,
    memberId: 200,
    documentType: 'RESIDENT_REGISTRATION',
    userName: '홍길동',
    phoneNo: '01012345678',
    identity: 'encrypted-ssn',
    easyAuthMethod: 'KAKAO'
  })
})
// Response: { documentId: 1, status: 'TWO_WAY_AUTH_REQUIRED', requestId: 'ext-123' }

// Step 2: 사용자가 카카오톡에서 인증 완료 (30초~1분 소요)
// → 프론트엔드에서 "카카오톡에서 인증을 완료해주세요" 안내

// Step 3: 인증 완료 후 실제 발급 (Pass-Through: 즉시 응답)
const res2 = await fetch('/api/documents/1/complete', {
  method: 'POST',
  body: JSON.stringify({
    requestId: 'ext-123'
  })
})
// Response: { status: 'PROCESSING', fileUrl: null, failureReason: null }
// HTTP 202 Accepted - 백그라운드에서 처리 중, 사용자는 더 이상 폴링하지 않음
```

### 설계 고려사항

#### ✅ 현재 설계 (Step 기반 API)
- **장점**:
  - 명확한 단계 구분
  - 프론트엔드가 인증 타이밍을 제어 가능
  - RESTful 하고 직관적
- **단점**:
  - 프론트엔드가 상태 관리 필요
  - 2-3번의 API 호출 필요

#### 💡 대안: 폴링 방식 (향후 고려)
```javascript
// 한 번의 API 호출로 시작
const res = await POST('/api/documents', { ... })

// 백엔드가 자동으로 2-way 인증 시작
// 프론트는 상태만 폴링
const interval = setInterval(async () => {
  const doc = await GET(`/api/documents/${res.documentId}`)

  if (doc.status === 'TWO_WAY_AUTH_REQUIRED') {
    showAlert('카카오톡에서 인증을 완료해주세요')
  } else if (doc.status === 'COMPLETED') {
    clearInterval(interval)
    showSuccess()
  }
}, 1000)
```

**폴링 방식의 장점**:
- 프론트엔드 코드가 더 간단
- 상태 관리를 백엔드에 위임
- WebSocket/SSE로 확장 가능

**폴링 방식의 단점**:
- 불필요한 네트워크 트래픽 (1초마다 GET 요청)
- 실시간성이 떨어질 수 있음 (최대 1초 지연)
- 서버 부하 증가 (많은 클라이언트 동시 폴링 시)

**결론**: 현재는 명확성을 위해 Step 기반 API 채택. 향후 트래픽이 증가하면 WebSocket/SSE 기반 실시간 알림으로 전환 고려.

## 보안 설계

### Transaction ID 매핑

외부 API의 실제 transaction ID를 프론트엔드에 직접 노출하지 않기 위해 **매핑 테이블**을 사용합니다.

**현재 문제:**
```javascript
// ❌ 위험: 외부 시스템의 실제 ID 노출
{
  "requestId": "codef-transaction-abc123-real-id"
}
```

**해결 방법:**
```
프론트엔드 ↔ 내부 UUID Token ↔ (DB 매핑 테이블) ↔ 외부 transaction ID
```

**매핑 테이블 구조:**
```kotlin
@Entity
@Table(name = "external_transaction_mappings")
data class ExternalTransactionMapping(
    @Id
    val internalToken: String,           // UUID (프론트엔드에 노출)
    val documentId: Long,
    val externalTransactionId: String,   // 실제 외부 API ID (숨김)
    val provider: String,                // "CODEF", "KOREA_PASS" 등
    val expiresAt: Instant               // 토큰 만료 시간 (10분)
)
```

**장점:**
- ✅ 외부 시스템의 ID 구조 노출 방지
- ✅ 토큰 만료 시간 제어 가능 (10분)
- ✅ 외부 시스템 변경 시에도 내부 API는 유지
- ✅ 감사 추적 (audit trail) 용이
- ✅ 보안 공격 표면 최소화

**사용 예시:**
```javascript
// Step 1: API 자동 발급 요청
POST /api/documents/issuance
Response: {
  "requestId": "550e8400-e29b-41d4-a716-446655440000"  // 내부 UUID
}

// Step 2: 인증 완료 후 발급 (내부 Token 사용)
POST /api/documents/1/complete
Request: {
  "requestId": "550e8400-e29b-41d4-a716-446655440000"
}

// 백엔드에서 UUID → 실제 외부 TX ID 매핑 후 외부 API 호출
```

**구현 참고:**
- 현재는 간소화를 위해 매핑 없이 Mock으로 구현
- 실제 운영 환경에서는 반드시 매핑 테이블 구현 필요

---

**작성자**: psyoung16 | [GitHub](https://github.com/psyoung16)