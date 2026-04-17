package com.github.psyoung16.delivery.presentation.controller

import io.kotest.core.extensions.ApplyExtension
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.extensions.spring.SpringExtension
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post

@WebMvcTest(DocumentController::class)
@ApplyExtension(SpringExtension::class)
class DocumentControllerTest(
    val mockMvc: MockMvc
) : DescribeSpec({

    describe("POST /api/documents") {
        context("서류 발급 요청 시") {
            it("201 Created 응답, documentId 반환") {
                mockMvc.post("/api/documents") {
                    contentType = MediaType.APPLICATION_JSON
                    content = """
                        {
                          "consentId": 100,
                          "memberId": 200,
                          "documentType": "RESIDENT_REGISTRATION",
                          "issuanceMethod": "API_ISSUED"
                        }
                    """.trimIndent()
                }.andExpect {
                    status { isCreated() }
                    jsonPath("$.documentId") { value(1) }
                }
            }
        }
    }

    describe("GET /api/documents/{id}") {
        context("서류가 존재할 때") {
            it("200 OK 응답, 서류 정보 반환") {
                mockMvc.get("/api/documents/1").andExpect {
                    status { isOk() }
                    jsonPath("$.id") { value(1) }
                    jsonPath("$.consentId") { value(100) }
                    jsonPath("$.memberId") { value(200) }
                    jsonPath("$.documentType") { value("RESIDENT_REGISTRATION") }
                    jsonPath("$.issuanceMethod") { value("API_ISSUED") }
                    jsonPath("$.status") { value("COMPLETED") }
                    jsonPath("$.fileUrl") { value("https://external-api.com/documents/issued-123.pdf") }
                    jsonPath("$.failureReason") { doesNotExist() }
                    jsonPath("$.failureType") { doesNotExist() }
                }
            }
        }

        context("서류가 존재하지 않을 때") {
            it("404 Not Found 응답, error 반환") {
                mockMvc.get("/api/documents/9999").andExpect {
                    status { isNotFound() }
                    jsonPath("$.status") { value(404) }
                    jsonPath("$.error") { value("NOT_FOUND") }
                    jsonPath("$.message") { exists() }
                }
            }
        }
    }

    describe("POST /api/documents/{id}/upload") {
        context("파일 업로드 성공 시") {
            it("204 No Content 응답") {
                mockMvc.post("/api/documents/1/upload") {
                    contentType = MediaType.APPLICATION_JSON
                    content = """
                        {
                          "fileUrl": "https://s3.amazonaws.com/documents/123.pdf"
                        }
                    """.trimIndent()
                }.andExpect {
                    status { isNoContent() }
                }
            }
        }

        context("서류가 존재하지 않을 때") {
            it("404 Not Found 응답, error 반환") {
                mockMvc.post("/api/documents/9999/upload") {
                    contentType = MediaType.APPLICATION_JSON
                    content = """
                        {
                          "fileUrl": "https://s3.amazonaws.com/documents/123.pdf"
                        }
                    """.trimIndent()
                }.andExpect {
                    status { isNotFound() }
                    jsonPath("$.status") { value(404) }
                    jsonPath("$.error") { value("NOT_FOUND") }
                    jsonPath("$.message") { exists() }
                }
            }
        }
    }

    describe("POST /api/documents/{id}/retry") {
        context("2차 인증 후 재시도 성공 시") {
            it("204 No Content 응답") {
                mockMvc.post("/api/documents/1/retry") {
                    contentType = MediaType.APPLICATION_JSON
                    content = """
                        {
                          "authToken": "valid-token"
                        }
                    """.trimIndent()
                }.andExpect {
                    status { isNoContent() }
                }
            }
        }

        context("서류가 존재하지 않을 때") {
            it("404 Not Found 응답, error 반환") {
                mockMvc.post("/api/documents/9999/retry") {
                    contentType = MediaType.APPLICATION_JSON
                    content = """
                        {
                          "authToken": "valid-token"
                        }
                    """.trimIndent()
                }.andExpect {
                    status { isNotFound() }
                    jsonPath("$.status") { value(404) }
                    jsonPath("$.error") { value("NOT_FOUND") }
                    jsonPath("$.message") { exists() }
                }
            }
        }

        context("잘못된 서류 상태에서 재시도 시") {
            it("400 Bad Request 응답, error 반환") {
                mockMvc.post("/api/documents/1/retry") {
                    contentType = MediaType.APPLICATION_JSON
                    content = """
                        {
                          "authToken": "INVALID_STATE"
                        }
                    """.trimIndent()
                }.andExpect {
                    status { isBadRequest() }
                    jsonPath("$.status") { value(400) }
                    jsonPath("$.error") { value("INVALID_STATE") }
                    jsonPath("$.message") { exists() }
                }
            }
        }
    }
})