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

### 1. Infrastructure Layer - Event Store (간소화)

#### 1.1 최소 구현
```kotlin
// Repository만 정의 (Spring Data JPA가 자동 구현)
interface DocumentEventRepository : JpaRepository<DocumentEvent, Long> {
    fun findByDocumentIdOrderByEventOrderAsc(documentId: Long): List<DocumentEvent>
}
```

**참고**:
- DocumentEvent Entity는 이미 구현됨 ✅
- H2 인메모리 DB로 충분 (별도 DB 설정 불필요)
- EventStore Service는 생략 (Repository로 직접 접근)
- Event 직렬화/역직렬화는 나중에 필요시 추가

### 2. Application Layer - Service (간소화 - 핵심만)

#### 2.1 DocumentIssuanceService (간단 버전)
```kotlin
@Service
class DocumentIssuanceService(
    private val documents: MutableMap<Long, Document> = mutableMapOf() // 인메모리 저장소
) {
    private var currentId = 1L

    // 서류 발급 요청
    fun requestDocument(
        consentId: ConsentId,
        memberId: MemberId,
        documentType: DocumentType,
        issuanceMethod: IssuanceMethod
    ): DocumentId {
        val documentId = DocumentId(currentId++)
        val (document, event) = Document.create(documentId, consentId, memberId, documentType, issuanceMethod)
        documents[documentId.value] = document
        return documentId
    }

    // 파일 업로드
    fun uploadFile(documentId: DocumentId, fileUrl: String) {
        val document = documents[documentId.value] ?: throw DocumentNotFoundException(documentId)
        val (updatedDocument, event) = document.uploadFile(fileUrl)
        documents[documentId.value] = updatedDocument
    }

    // 외부 API 발급 시작
    fun startProcessing(documentId: DocumentId) {
        val document = documents[documentId.value] ?: throw DocumentNotFoundException(documentId)
        val (updatedDocument, event) = document.startProcessing()
        documents[documentId.value] = updatedDocument
    }

    // 서류 조회
    fun getDocument(documentId: DocumentId): Document {
        return documents[documentId.value] ?: throw DocumentNotFoundException(documentId)
    }
}
```

**간소화 내용**:
- 인메모리 Map으로 Document 저장 (Repository 생략)
- Event 발행 로직 생략 (나중에 추가)
- ExternalApiClient Mock 생략 (Controller에서 직접 호출)
- EventHandler 생략 (비동기 처리 나중에)

### 3. Presentation Layer - Controller 연결

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

### 4. 구현 순서 (간소화)

1. **Step 1: Application Service 구현** (핵심)
   - DocumentIssuanceService 클래스 생성
   - 인메모리 Map으로 Document 관리
   - 기본 CRUD 메서드 구현

2. **Step 2: Controller와 Service 연결**
   - DocumentController의 stub 코드를 실제 Service 호출로 변경
   - DTO 변환 로직 추가
   - 간단한 통합 테스트

3. **Step 3: 애플리케이션 실행 확인**
   - Spring Boot 실행
   - Postman/curl로 API 테스트
   - H2 콘솔에서 데이터 확인

### 5. 기술 스택

- **Framework**: Spring Boot 3.5.8
- **Language**: Kotlin 2.2.21
- **DB**: H2 (in-memory)
- **ORM**: Spring Data JPA
- **Test**: Kotest
- **Build**: Gradle Kotlin DSL

### 6. 향후 확장 가능성

#### 6.1 Event 영속화 (나중에)
- DocumentEventRepository 구현
- Event 직렬화/역직렬화
- Event Replay 기능

#### 6.2 비동기 처리 (나중에)
- Spring Events 기반 Event Handler
- ExternalApiClient Mock 구현
- 타임아웃 로직 (2-3초)

#### 6.3 메시징 시스템 (나중에)
- Kafka/RabbitMQ 통합
- Saga 패턴 구현
- 분산 트랜잭션 관리

## Next Actions

1. DocumentIssuanceService 구현 (간단 버전)
2. DocumentController와 Service 연결
3. 통합 테스트 및 실행 확인
4. Git commit & push