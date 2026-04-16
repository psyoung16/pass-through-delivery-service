# 도메인 설계

## 설계 원칙

**Pass-Through 아키텍처**: Consent(동의서)와 Document(첨부서류)는 **완전히 독립된 도메인**입니다.
- 동의서는 첨부서류 상태와 무관하게 제출 완료
- 첨부서류는 백그라운드에서 독립적으로 발급 처리
- 두 도메인은 이벤트를 통해서만 통신

## 도메인 모델

### 1. Consent (동의서)

재개발/재건축 사업 동의서

```kotlin
class Consent(
    val id: ConsentId,
    val memberId: MemberId,
    val personalInfo: PersonalInfo,        // 인적사항
    val agreementContent: AgreementContent, // 동의내용
    val status: ConsentStatus,
    val submittedAt: Instant?
)

enum class ConsentStatus {
    DRAFT,      // 작성중
    SUBMITTED   // 제출완료
}
```

**중요**: Consent는 Document의 존재를 알지 못합니다.

### 2. Document (첨부서류)

법적 서류 (외부 기관 자동 발급 또는 사용자 직접 업로드)

```kotlin
class Document(
    val id: DocumentId,
    val consentId: ConsentId,
    val type: DocumentType,
    val issuanceMethod: IssuanceMethod,  // 발급 방식
    val status: DocumentStatus,
    val issuer: Issuer?,                 // API 발급시만 사용
    val uploadedFileUrl: String?,        // 사용자 업로드시만 사용
    val requestedAt: Instant,
    val completedAt: Instant?,
    val failureReason: String?
)

enum class DocumentType {
    RESIDENT_REGISTRATION,  // 주민등록등본
    FAMILY_RELATION         // 가족관계증명서
}

enum class IssuanceMethod {
    API_ISSUED,      // 외부 API 자동 발급 (비동기)
    USER_UPLOADED    // 사용자 직접 업로드 (동기)
}

enum class DocumentStatus {
    REQUESTED,    // 발급/업로드 요청
    PROCESSING,   // API 발급 처리중
    COMPLETED,    // 완료
    FAILED        // 실패 (API 발급시만)
}

enum class Issuer {
    GOVERNMENT24,  // 정부24
    SUPREME_COURT  // 대법원
}
```

### 3. Member (조합원)

재개발/재건축 조합원

```kotlin
class Member(
    val id: MemberId,
    val name: String,
    val residentNumber: String,
    val contactInfo: ContactInfo
)
```

## 이벤트 설계

### Consent 이벤트

```kotlin
// 동의서 제출
data class ConsentSubmitted(
    val consentId: ConsentId,
    val memberId: MemberId,
    val submittedAt: Instant,
    val requiredDocuments: List<DocumentType>  // 필요한 첨부서류 목록
) : DomainEvent
```

### Document 이벤트

```kotlin
// 서류 발급 요청 (API)
data class DocumentRequested(
    val documentId: DocumentId,
    val consentId: ConsentId,
    val documentType: DocumentType,
    val issuanceMethod: IssuanceMethod,
    val issuer: Issuer?,
    val requestedAt: Instant
) : DomainEvent

// 서류 업로드 (사용자)
data class DocumentUploaded(
    val documentId: DocumentId,
    val consentId: ConsentId,
    val documentType: DocumentType,
    val uploadedFileUrl: String,
    val uploadedAt: Instant
) : DomainEvent

// 서류 발급 완료 (API)
data class DocumentIssued(
    val documentId: DocumentId,
    val consentId: ConsentId,
    val issuedAt: Instant,
    val documentUrl: String
) : DomainEvent

// 서류 발급 실패 (API)
data class DocumentIssueFailed(
    val documentId: DocumentId,
    val consentId: ConsentId,
    val failureReason: String,
    val failedAt: Instant
) : DomainEvent

// 서류 재제출
data class DocumentResubmitted(
    val documentId: DocumentId,
    val consentId: ConsentId,
    val previousFailureReason: String,
    val resubmittedAt: Instant
) : DomainEvent

// 2-way 인증 요청 (API 발급시)
data class TwoWayAuthRequired(
    val documentId: DocumentId,
    val consentId: ConsentId,
    val transactionId: String,      // 외부 API 트랜잭션 ID
    val jti: String,                 // 2차 요청용 토큰
    val authUrl: String,             // 본인인증 URL (카카오, 금융기관 등)
    val requiredAt: Instant
) : DomainEvent

// 사용자 본인인증 완료
data class UserAuthenticated(
    val documentId: DocumentId,
    val consentId: ConsentId,
    val authResult: String,
    val authenticatedAt: Instant
) : DomainEvent
```

## 상태 전이도

### Consent 상태 전이

```
DRAFT → SUBMITTED
```

### Document 상태 전이

**[API_ISSUED: 외부 API 자동 발급]**

*일반 플로우:*
```
REQUESTED
    ↓
PROCESSING (외부 API 호출)
    ↓
    ├─→ COMPLETED (성공)
    └─→ FAILED (실패) → 재제출 → REQUESTED
```

*2-way 인증 플로우:*
```
REQUESTED
    ↓
PROCESSING (1차 API 호출)
    ↓
TWO_WAY_AUTH_REQUIRED (본인인증 대기)
    ↓
사용자 인증 (카카오, 금융기관 등)
    ↓
PROCESSING (2차 API 호출)
    ↓
    ├─→ COMPLETED (성공)
    └─→ FAILED (실패) → 재제출 → REQUESTED
```

**[USER_UPLOADED: 사용자 직접 업로드]**
```
REQUESTED → COMPLETED (즉시)
```

**참고:** 상태는 이벤트 스트림에서 계산됩니다. 실제 DB에는 이벤트만 저장하고, 상태는 조회 시 이벤트를 replay하여 도출합니다.

## Pass-Through 플로우

### 플로우 A: API 자동 발급 (비동기)

```
1. 사용자: 동의서 제출
   → Consent: DRAFT → SUBMITTED
   → 이벤트: ConsentSubmitted 발행

2. Document Handler: ConsentSubmitted 이벤트 구독
   → Document 생성 (REQUESTED, API_ISSUED)
   → 이벤트: DocumentRequested 발행

3. External API Handler: DocumentRequested 이벤트 구독
   → 외부 API 호출 (비동기, 10초~3분)
   → Document: PROCESSING

4-a. 성공: Document: COMPLETED
     → 이벤트: DocumentIssued 발행

4-b. 실패: Document: FAILED
     → 이벤트: DocumentIssueFailed 발행
     → 운영자 재처리
     → 이벤트: DocumentResubmitted 발행
```

### 플로우 B: API 자동 발급 with 2-way 인증 (비동기)

```
1. 사용자: 동의서 제출
   → Consent: DRAFT → SUBMITTED
   → 이벤트: ConsentSubmitted 발행

2. Document Handler: ConsentSubmitted 이벤트 구독
   → Document 생성 (REQUESTED, API_ISSUED)
   → 이벤트: DocumentRequested 발행

3. External API Handler: 1차 API 호출
   → 이벤트: TwoWayAuthRequired 발행
   → 본인인증 URL 전달 (카카오, 금융기관 등)

4. 사용자: 외부 인증 완료
   → 이벤트: UserAuthenticated 발행

5. External API Handler: 2차 API 호출
   → Document: PROCESSING

6-a. 성공: Document: COMPLETED
     → 이벤트: DocumentIssued 발행

6-b. 실패: Document: FAILED
     → 이벤트: DocumentIssueFailed 발행
     → 운영자 재처리
```

**핵심:** 1차 요청 후 2-way 인증 대기 중에도 Consent는 SUBMITTED 상태 유지. 사용자는 다른 작업 가능.

### 플로우 C: 사용자 업로드 (동기)

```
1. 사용자: 동의서 제출 + 서류 사진 업로드
   → Consent: DRAFT → SUBMITTED
   → 이벤트: ConsentSubmitted 발행

2. Document Handler: 사용자가 파일 업로드
   → Document 생성 (REQUESTED, USER_UPLOADED)
   → Document: COMPLETED (즉시)
   → 이벤트: DocumentUploaded 발행
```

**핵심**: Consent는 Document 처리 과정과 완전히 무관합니다. API 발급 실패, 2-way 인증 대기, 업로드 여부와 상관없이 Consent는 SUBMITTED 상태를 유지합니다.

## 비즈니스 규칙

### 1. Pass-Through 원칙
- 동의서는 첨부서류 상태와 무관하게 제출 완료 (`SUBMITTED`)
- 첨부서류는 백그라운드에서 독립적으로 처리
- Consent 도메인은 Document 도메인을 참조하지 않음

### 2. 첨부서류 제출 방식
- **API 자동 발급**: 외부 기관 API 호출하여 자동 발급 (비동기, 10초~3분)
- **사용자 직접 업로드**: 사용자가 서류 사진 찍어서 업로드 (동기, 즉시 완료)
- 조합원이 두 방식 중 선택 가능

### 3. 이벤트 기반 통신
- Consent 제출 시 `ConsentSubmitted` 이벤트 발행
- Document Handler가 이벤트를 구독하여 첨부서류 처리 시작
- 두 도메인 간 직접 참조 없음

### 4. 첨부서류 발급 재시도 (API 발급시만)
- 발급 실패 시 `FAILED` 상태로 전환
- 운영자가 재제출 가능
- 재제출 시 `DocumentResubmitted` 이벤트 발행 후 `REQUESTED`로 복귀

### 5. 이벤트 소싱
- 모든 Document 상태 변경은 이벤트로 기록
- `요청 → 실패 → 재제출 → 완료` 전체 히스토리 추적 가능
- API 발급과 사용자 업로드 모두 이벤트 스트림에 기록

### 6. 외부 API 연동 (API 발급시만)
- 정부24: 주민등록등본
- 대법원: 가족관계증명서
- API 호출 시간: 10초 ~ 3분
- 비동기 처리로 사용자는 대기하지 않음

### 7. 2-way 인증 처리 (간편발급)
- 외부 API는 본인인증을 위해 2-way 인증 필요
- **1차 요청**: 서류 발급 요청 → `TwoWayAuthRequired` 이벤트
- **사용자 인증**: 카카오, 금융기관 등에서 본인인증
- **2차 요청**: 인증 완료 후 실제 서류 발급
- 인증 대기 중에도 Consent는 SUBMITTED 상태 유지 (Pass-Through)