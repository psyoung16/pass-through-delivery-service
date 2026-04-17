# 구현 계획 (Implementation Plan)

> 이 문서는 임시 계획 문서로, 완료 후 삭제될 예정입니다.

## 현재 완료 상태

### ✅ Domain Layer (완료)
- [x] Event Sourcing 기반 Document Aggregate
- [x] 7개 Domain Event 정의
  - DocumentRequested
  - DocumentUploaded
  - ProcessingStarted
  - TwoWayAuthRequired
  - ProcessingRetried
  - DocumentIssued
  - DocumentIssueFailed
- [x] FailureType enum (RETRYABLE, PERMANENT)
- [x] 5개 사용자 시나리오 테스트 (BDD 스타일)

## 남은 구현 계획

### 1. Infrastructure Layer - Event Store

#### 1.1 EventStore Interface
```kotlin
interface DocumentEventStore {
    fun save(documentId: DocumentId, event: DocumentDomainEvent)
    fun loadEvents(documentId: DocumentId): List<DocumentDomainEvent>
}
```

#### 1.2 구현 옵션
- **Option A**: In-Memory (테스트용)
- **Option B**: RDBMS 기반 (PostgreSQL)
  - 테이블: `document_events`
  - 컬럼: `id`, `document_id`, `event_type`, `event_data` (JSON), `occurred_at`, `sequence`
- **Option C**: Event Store 전용 DB (EventStoreDB - 오버엔지니어링 가능성)

**추천**: Option B (RDBMS) - 실무에서 가장 일반적, 포트폴리오에 적합

#### 1.3 필요한 클래스
- `DocumentEventJpaRepository` (JPA Repository)
- `DocumentEventEntity` (JPA Entity)
- `DocumentEventStoreImpl` (EventStore 구현체)
- Event Serialization/Deserialization 로직

### 2. Application Layer - Service

#### 2.1 DocumentIssuanceService
```kotlin
interface DocumentIssuanceService {
    // 서류 발급 요청 (사용자 업로드 방식)
    fun requestDocumentWithUpload(request: RequestDocumentCommand): DocumentId

    // 서류 발급 요청 (API 자동 발급 방식)
    fun requestDocumentWithApi(request: RequestDocumentCommand): DocumentId

    // 사용자가 직접 파일 업로드
    fun uploadFile(documentId: DocumentId, fileUrl: String)

    // 외부 API 발급 프로세스 시작 (비동기 트리거)
    fun startApiIssuance(documentId: DocumentId)

    // 서류 조회
    fun getDocument(documentId: DocumentId): DocumentDto
}
```

#### 2.2 ExternalApiClient (Infrastructure)
외부 기관 API 호출을 담당하는 클라이언트

```kotlin
interface ExternalDocumentApiClient {
    // 서류 발급 요청
    fun requestIssuance(request: IssuanceRequest): IssuanceResponse

    // 2차 인증 후 재시도
    fun retryWithAuth(transactionId: String, authToken: String): IssuanceResponse
}
```

**응답 시간 기반 로직**:
- 200ms 이내 응답 + 실패 사유 → FAILED
- 2-3초 타임아웃 → SUCCESS (비동기 처리 중)
- 2차 인증 필요 응답 → TWO_WAY_AUTH_REQUIRED

#### 2.3 EventHandler (비동기 처리)
Event를 구독하여 외부 API 호출 등을 처리

```kotlin
@Component
class DocumentEventHandler(
    private val externalApiClient: ExternalDocumentApiClient,
    private val eventStore: DocumentEventStore
) {
    @EventListener
    fun on(event: ProcessingStarted) {
        // 비동기로 외부 API 호출
        // 결과에 따라 DocumentIssued/TwoWayAuthRequired/DocumentIssueFailed 이벤트 발행
    }

    @EventListener
    fun on(event: ProcessingRetried) {
        // 2차 인증 완료 후 재시도 로직
    }
}
```

### 3. Presentation Layer - REST API (Optional)

#### 3.1 DocumentController
```kotlin
@RestController
@RequestMapping("/api/documents")
class DocumentController(
    private val documentIssuanceService: DocumentIssuanceService
) {
    // POST /api/documents - 서류 발급 요청
    @PostMapping
    fun requestDocument(@RequestBody request: RequestDocumentRequest): ResponseEntity<DocumentResponse>

    // POST /api/documents/{id}/upload - 파일 업로드
    @PostMapping("/{id}/upload")
    fun uploadFile(@PathVariable id: Long, @RequestBody request: UploadFileRequest): ResponseEntity<Void>

    // GET /api/documents/{id} - 서류 조회
    @GetMapping("/{id}")
    fun getDocument(@PathVariable id: Long): ResponseEntity<DocumentResponse>

    // POST /api/documents/{id}/retry - 2차 인증 후 재시도
    @PostMapping("/{id}/retry")
    fun retryAfterAuth(@PathVariable id: Long, @RequestBody request: RetryRequest): ResponseEntity<Void>
}
```

#### 3.2 Response DTO
```kotlin
data class DocumentResponse(
    val id: Long,
    val status: String,
    val documentType: String,
    val issuanceMethod: String,
    val fileUrl: String?,
    val failureReason: String?,
    val failureType: String?
)
```

### 4. 구현 순서 제안

1. **Phase 1: Event Store (Infrastructure)**
   - DocumentEventEntity, DocumentEventJpaRepository 생성
   - DocumentEventStoreImpl 구현
   - 통합 테스트 작성

2. **Phase 2: Application Service**
   - DocumentIssuanceService 구현
   - ExternalApiClient Mock 구현 (실제 API 없으므로)
   - Service Layer 테스트 작성

3. **Phase 3: Event Handler (비동기 처리)**
   - Spring Event 기반 DocumentEventHandler 구현
   - 타임아웃 로직 구현 (2-3초)
   - 통합 테스트

4. **Phase 4: REST API (선택적)**
   - Controller 구현
   - API 통합 테스트
   - Swagger/OpenAPI 문서 생성

### 5. 기술 스택

- **Framework**: Spring Boot 3.x
- **Language**: Kotlin
- **DB**: PostgreSQL (Event Store)
- **ORM**: Spring Data JPA
- **Test**: Kotest, MockK
- **API Doc**: SpringDoc OpenAPI (optional)
- **Async**: Spring Events or Kotlin Coroutines

### 6. 고려 사항

#### 6.1 동시성 제어
- Event Store에 저장 시 `sequence` 필드로 순서 보장
- Optimistic Locking 고려 (version 필드)

#### 6.2 트랜잭션 관리
- Event 저장과 도메인 로직은 동일 트랜잭션
- 외부 API 호출은 별도 트랜잭션 (비동기)

#### 6.3 보안
- TransactionToken (UUID) 매핑으로 실제 외부 transactionId 노출 방지
- 사용자에게는 UUID만 반환, 내부적으로 실제 ID 매핑

#### 6.4 에러 처리
- FailureType에 따른 재시도 전략
  - RETRYABLE: 자동 재시도 (exponential backoff)
  - PERMANENT: 재시도 중단, 사용자 액션 필요

#### 6.5 모니터링
- 각 상태별 이벤트 발행 수 추적
- 외부 API 응답 시간 모니터링
- 실패율 통계

### 7. 포트폴리오 관점에서의 강점

- **Event Sourcing**: 트렌디한 아키텍처 패턴 적용
- **DDD**: Aggregate, Domain Event 등 전술적 패턴 활용
- **Clean Architecture**: 계층 분리 (Domain → Application → Infrastructure → Presentation)
- **비동기 처리**: 외부 API 타임아웃 로직, Event-Driven
- **테스트**: BDD 스타일 도메인 테스트, 통합 테스트
- **Kotlin**: 현대적 JVM 언어 활용

## Next Actions

1. Event Store 구현부터 시작
2. 각 Phase별로 commit
3. 완료 후 이 문서 삭제