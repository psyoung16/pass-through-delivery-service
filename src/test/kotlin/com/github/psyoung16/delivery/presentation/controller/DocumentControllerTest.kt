package com.github.psyoung16.delivery.presentation.controller

import com.github.psyoung16.delivery.application.service.DocumentIssuanceService
import com.github.psyoung16.delivery.domain.consent.ConsentId
import com.github.psyoung16.delivery.domain.document.Document
import com.github.psyoung16.delivery.domain.document.DocumentId
import com.github.psyoung16.delivery.domain.document.DocumentType
import com.github.psyoung16.delivery.domain.document.IssuanceMethod
import com.github.psyoung16.delivery.domain.member.MemberId
import com.github.psyoung16.delivery.presentation.exception.DocumentNotFoundException
import com.ninjasquad.springmockk.MockkBean
import io.kotest.core.extensions.ApplyExtension
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.extensions.spring.SpringExtension
import io.mockk.every
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post

@WebMvcTest(DocumentController::class)
@ApplyExtension(SpringExtension::class)
class DocumentControllerTest(
    val mockMvc: MockMvc,
    @MockkBean val documentIssuanceService: DocumentIssuanceService,
    @MockkBean val externalApiClient: com.github.psyoung16.delivery.infrastructure.external.ExternalApiClient
) : DescribeSpec({

    describe("POST /api/documents/upload") {
        context("사용자 직접 업로드 시") {
            it("201 Created 응답, documentId 반환") {
                // given
                val documentId = DocumentId(1L)
                every {
                    documentIssuanceService.requestDocument(
                        consentId = ConsentId(100L),
                        memberId = MemberId(200L),
                        documentType = DocumentType.RESIDENT_REGISTRATION,
                        issuanceMethod = IssuanceMethod.USER_UPLOADED
                    )
                } returns documentId

                every {
                    documentIssuanceService.uploadFile(
                        documentId = documentId,
                        fileUrl = "https://s3.amazonaws.com/documents/123.pdf"
                    )
                } returns Unit

                // when & then
                mockMvc.post("/api/documents/upload") {
                    contentType = MediaType.APPLICATION_JSON
                    content = """
                        {
                          "consentId": 100,
                          "memberId": 200,
                          "documentType": "RESIDENT_REGISTRATION",
                          "fileUrl": "https://s3.amazonaws.com/documents/123.pdf"
                        }
                    """.trimIndent()
                }.andExpect {
                    status { isCreated() }
                    jsonPath("$.documentId") { value(1) }
                }
            }
        }
    }

    describe("POST /api/documents/issuance") {
        context("API 자동 발급 요청 시") {
            it("201 Created 응답, TWO_WAY_AUTH_REQUIRED 상태 반환") {
                // given
                val documentId = DocumentId(1L)
                every {
                    documentIssuanceService.requestDocument(
                        consentId = ConsentId(100L),
                        memberId = MemberId(200L),
                        documentType = DocumentType.RESIDENT_REGISTRATION,
                        issuanceMethod = IssuanceMethod.API_ISSUED
                    )
                } returns documentId

                every {
                    externalApiClient.requestEasyAuth(any(), any(), any(), any())
                } returns "mock-tx-123"

                every {
                    documentIssuanceService.requireTwoWayAuth(documentId, any())
                } returns Unit

                // when & then
                mockMvc.post("/api/documents/issuance") {
                    contentType = MediaType.APPLICATION_JSON
                    content = """
                        {
                          "consentId": 100,
                          "memberId": 200,
                          "documentType": "RESIDENT_REGISTRATION",
                          "userName": "홍길동",
                          "phoneNo": "01012345678",
                          "identity": "encrypted-ssn",
                          "easyAuthMethod": "KAKAO"
                        }
                    """.trimIndent()
                }.andExpect {
                    status { isCreated() }
                    jsonPath("$.documentId") { value(1) }
                    jsonPath("$.status") { value("TWO_WAY_AUTH_REQUIRED") }
                    jsonPath("$.requestId") { exists() }
                    jsonPath("$.message") { value("KAKAO 인증을 진행해주세요") }
                }
            }
        }
    }

    describe("POST /api/documents/{id}/complete") {
        context("2-way 인증 완료 후 실제 발급 시") {
            it("200 OK 응답, COMPLETED 상태 반환") {
                // given
                val documentId = DocumentId(1L)
                every {
                    documentIssuanceService.retryProcessing(documentId)
                } returns Unit

                every {
                    externalApiClient.issueDocument(any())
                } returns "https://external-api.com/documents/issued-123.pdf"

                every {
                    documentIssuanceService.issueDocument(documentId, any())
                } returns Unit

                // when & then
                mockMvc.post("/api/documents/1/complete") {
                    contentType = MediaType.APPLICATION_JSON
                    content = """
                        {
                          "requestId": "ext-request-123"
                        }
                    """.trimIndent()
                }.andExpect {
                    status { isOk() }
                    jsonPath("$.status") { value("COMPLETED") }
                    jsonPath("$.fileUrl") { exists() }
                    jsonPath("$.failureReason") { doesNotExist() }
                }
            }
        }
    }

    describe("GET /api/documents/{id}") {
        context("서류가 존재할 때") {
            it("200 OK 응답, 서류 정보 반환") {
                // given
                val (document, _) = Document.create(
                    id = DocumentId(1L),
                    consentId = ConsentId(100L),
                    memberId = MemberId(200L),
                    documentType = DocumentType.RESIDENT_REGISTRATION,
                    issuanceMethod = IssuanceMethod.USER_UPLOADED
                )
                val (updatedDocument, _) = document.uploadFile("https://s3.amazonaws.com/documents/123.pdf")

                every {
                    documentIssuanceService.getDocument(DocumentId(1L))
                } returns updatedDocument

                // when & then
                mockMvc.get("/api/documents/1").andExpect {
                    status { isOk() }
                    jsonPath("$.id") { value(1) }
                    jsonPath("$.consentId") { value(100) }
                    jsonPath("$.memberId") { value(200) }
                    jsonPath("$.documentType") { value("RESIDENT_REGISTRATION") }
                    jsonPath("$.issuanceMethod") { value("USER_UPLOADED") }
                    jsonPath("$.status") { value("COMPLETED") }
                    jsonPath("$.fileUrl") { value("https://s3.amazonaws.com/documents/123.pdf") }
                }
            }
        }

        context("서류가 존재하지 않을 때") {
            it("404 Not Found 응답, error 반환") {
                // given
                every {
                    documentIssuanceService.getDocument(DocumentId(9999L))
                } throws DocumentNotFoundException(DocumentId(9999L))

                // when & then
                mockMvc.get("/api/documents/9999").andExpect {
                    status { isNotFound() }
                    jsonPath("$.status") { value(404) }
                    jsonPath("$.error") { value("NOT_FOUND") }
                    jsonPath("$.message") { exists() }
                }
            }
        }
    }
})