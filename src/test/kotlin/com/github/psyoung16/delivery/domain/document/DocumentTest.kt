package com.github.psyoung16.delivery.domain.document

import com.github.psyoung16.delivery.domain.consent.ConsentId
import com.github.psyoung16.delivery.domain.document.events.DocumentIssueFailed
import com.github.psyoung16.delivery.domain.document.events.DocumentIssued
import com.github.psyoung16.delivery.domain.document.events.DocumentRequested
import com.github.psyoung16.delivery.domain.document.events.DocumentUploaded
import com.github.psyoung16.delivery.domain.document.events.ProcessingRetried
import com.github.psyoung16.delivery.domain.document.events.ProcessingStarted
import com.github.psyoung16.delivery.domain.document.events.TwoWayAuthRequired
import com.github.psyoung16.delivery.domain.member.MemberId
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

class DocumentTest : DescribeSpec({

    describe("시나리오 1: 사용자가 직접 서류를 업로드한다") {
        val documentId = DocumentId(1L)
        val consentId = ConsentId(100L)
        val memberId = MemberId(200L)
        val documentType = DocumentType.RESIDENT_REGISTRATION
        val issuanceMethod = IssuanceMethod.USER_UPLOADED

        context("서류 발급을 요청하고") {
            val (document, event) = Document.create(
                id = documentId,
                consentId = consentId,
                memberId = memberId,
                documentType = documentType,
                issuanceMethod = issuanceMethod
            )

            it("DocumentRequested 이벤트가 발행된다") {
                event.shouldBeInstanceOf<DocumentRequested>()
                event.documentId shouldBe documentId
                event.issuanceMethod shouldBe issuanceMethod
            }

            it("상태는 REQUESTED이다") {
                document.status() shouldBe DocumentStatus.REQUESTED
            }

            context("파일을 첨부하면") {
                val fileUrl = "https://s3.amazonaws.com/documents/123.pdf"
                val (updatedDocument, uploadEvent) = document.uploadFile(fileUrl)

                it("DocumentUploaded 이벤트가 발행된다") {
                    uploadEvent.shouldBeInstanceOf<DocumentUploaded>()
                    uploadEvent.documentId shouldBe documentId
                    uploadEvent.fileUrl shouldBe fileUrl
                }

                it("상태는 COMPLETED이다") {
                    updatedDocument.status() shouldBe DocumentStatus.COMPLETED
                }
            }
        }
    }

    describe("시나리오 2: API로 서류를 자동 발급받는다 (정상)") {
        val documentId = DocumentId(2L)
        val consentId = ConsentId(100L)
        val memberId = MemberId(200L)
        val documentType = DocumentType.RESIDENT_REGISTRATION
        val issuanceMethod = IssuanceMethod.API_ISSUED

        context("서류 발급을 요청하고") {
            val (document, event) = Document.create(
                id = documentId,
                consentId = consentId,
                memberId = memberId,
                documentType = documentType,
                issuanceMethod = issuanceMethod
            )

            it("DocumentRequested 이벤트가 발행된다") {
                event.shouldBeInstanceOf<DocumentRequested>()
                event.documentId shouldBe documentId
                event.consentId shouldBe consentId
                event.memberId shouldBe memberId
                event.documentType shouldBe documentType
                event.issuanceMethod shouldBe issuanceMethod
            }

            it("상태는 REQUESTED이다") {
                document.status() shouldBe DocumentStatus.REQUESTED
            }

            context("외부 기관에 발급을 요청하면") {
                val (processingDocument, processingEvent) = document.startProcessing()

                it("ProcessingStarted 이벤트가 발행된다") {
                    processingEvent.shouldBeInstanceOf<ProcessingStarted>()
                    processingEvent.documentId shouldBe documentId
                }

                it("상태는 PROCESSING이다") {
                    processingDocument.status() shouldBe DocumentStatus.PROCESSING
                }

                context("외부 기관에서 성공적으로 발급하면") {
                    // 비즈니스 규칙: 타임아웃 발생(2-3초 응답 없음) = 성공으로 간주
                    val fileUrl = "https://external-api.com/documents/issued-123.pdf"
                    val (issuedDocument, issuedEvent) = processingDocument.issueDocument(fileUrl)

                    it("DocumentIssued 이벤트가 발행된다") {
                        issuedEvent.shouldBeInstanceOf<DocumentIssued>()
                        issuedEvent.documentId shouldBe documentId
                        issuedEvent.fileUrl shouldBe fileUrl
                    }

                    it("상태는 COMPLETED이다") {
                        issuedDocument.status() shouldBe DocumentStatus.COMPLETED
                    }
                }
            }
        }
    }

    describe("시나리오 3: API 발급 시 2차 인증이 필요하다") {
        val documentId = DocumentId(3L)
        val consentId = ConsentId(100L)
        val memberId = MemberId(200L)
        val documentType = DocumentType.RESIDENT_REGISTRATION
        val issuanceMethod = IssuanceMethod.API_ISSUED

        context("서류 발급을 요청하고") {
            val (document, _) = Document.create(
                id = documentId,
                consentId = consentId,
                memberId = memberId,
                documentType = documentType,
                issuanceMethod = issuanceMethod
            )

            context("외부 기관에서 2차 인증을 요구하면") {
                val reason = "본인 인증 필요"
                val (authRequiredDocument, authEvent) = document.requireTwoWayAuth(reason)

                it("TwoWayAuthRequired 이벤트가 발행된다") {
                    authEvent.shouldBeInstanceOf<TwoWayAuthRequired>()
                    authEvent.documentId shouldBe documentId
                    authEvent.reason shouldBe reason
                }

                it("상태는 TWO_WAY_AUTH_REQUIRED이다") {
                    authRequiredDocument.status() shouldBe DocumentStatus.TWO_WAY_AUTH_REQUIRED
                }

                context("사용자가 인증을 완료하고 재시도하면") {
                    // 비즈니스 규칙: 사용자가 외부 인증 완료 → 시스템이 자동 재시도
                    val (retriedDocument, retriedEvent) = authRequiredDocument.retryProcessing()

                    it("ProcessingRetried 이벤트가 발행된다") {
                        retriedEvent.shouldBeInstanceOf<ProcessingRetried>()
                        retriedEvent.documentId shouldBe documentId
                    }

                    it("상태는 PROCESSING이다") {
                        retriedDocument.status() shouldBe DocumentStatus.PROCESSING
                    }

                    context("재시도 후 성공적으로 발급하면") {
                        val fileUrl = "https://external-api.com/documents/retried-issued-123.pdf"
                        val (issuedDocument, issuedEvent) = retriedDocument.issueDocument(fileUrl)

                        it("DocumentIssued 이벤트가 발행된다") {
                            issuedEvent.shouldBeInstanceOf<DocumentIssued>()
                            issuedEvent.documentId shouldBe documentId
                            issuedEvent.fileUrl shouldBe fileUrl
                        }

                        it("상태는 COMPLETED이다") {
                            issuedDocument.status() shouldBe DocumentStatus.COMPLETED
                        }
                    }
                }
            }
        }
    }

    describe("시나리오 4: API 발급이 실패한다") {
        val documentId = DocumentId(4L)
        val consentId = ConsentId(100L)
        val memberId = MemberId(200L)
        val documentType = DocumentType.RESIDENT_REGISTRATION
        val issuanceMethod = IssuanceMethod.API_ISSUED

        context("서류 발급을 요청하고") {
            val (document, _) = Document.create(
                id = documentId,
                consentId = consentId,
                memberId = memberId,
                documentType = documentType,
                issuanceMethod = issuanceMethod
            )

            context("외부 기관에서 발급을 거부하면") {
                // 비즈니스 규칙: 즉시 응답(200ms 이내) + 실패 사유 포함
                val failureReason = "주민등록번호가 일치하지 않습니다"
                val (failedDocument, failedEvent) = document.failIssuance(failureReason, FailureType.PERMANENT)

                it("DocumentIssueFailed 이벤트가 발행된다") {
                    failedEvent.shouldBeInstanceOf<DocumentIssueFailed>()
                    failedEvent.documentId shouldBe documentId
                    failedEvent.reason shouldBe failureReason
                    failedEvent.failureType shouldBe FailureType.PERMANENT
                }

                it("상태는 FAILED이다") {
                    failedDocument.status() shouldBe DocumentStatus.FAILED
                }
            }
        }
    }

    describe("시나리오 5: 2차 인증 후에도 발급이 실패한다") {
        val documentId = DocumentId(5L)
        val consentId = ConsentId(100L)
        val memberId = MemberId(200L)
        val documentType = DocumentType.RESIDENT_REGISTRATION
        val issuanceMethod = IssuanceMethod.API_ISSUED

        context("서류 발급을 요청하고") {
            val (document, _) = Document.create(
                id = documentId,
                consentId = consentId,
                memberId = memberId,
                documentType = documentType,
                issuanceMethod = issuanceMethod
            )

            context("외부 기관에서 2차 인증을 요구하면") {
                val reason = "본인 인증 필요"
                val (authRequiredDocument, _) = document.requireTwoWayAuth(reason)

                it("상태는 TWO_WAY_AUTH_REQUIRED이다") {
                    authRequiredDocument.status() shouldBe DocumentStatus.TWO_WAY_AUTH_REQUIRED
                }

                context("사용자가 인증을 완료하고 재시도했지만") {
                    val (retriedDocument, retriedEvent) = authRequiredDocument.retryProcessing()

                    it("ProcessingRetried 이벤트가 발행된다") {
                        retriedEvent.shouldBeInstanceOf<ProcessingRetried>()
                        retriedEvent.documentId shouldBe documentId
                    }

                    it("상태는 PROCESSING이다") {
                        retriedDocument.status() shouldBe DocumentStatus.PROCESSING
                    }

                    context("외부 기관에서 발급을 거부하면") {
                        val failureReason = "외부 기관 시스템 점검 중입니다"
                        val (failedDocument, failedEvent) = retriedDocument.failIssuance(failureReason, FailureType.RETRYABLE)

                        it("DocumentIssueFailed 이벤트가 발행된다") {
                            failedEvent.shouldBeInstanceOf<DocumentIssueFailed>()
                            failedEvent.documentId shouldBe documentId
                            failedEvent.reason shouldBe failureReason
                            failedEvent.failureType shouldBe FailureType.RETRYABLE
                        }

                        it("상태는 FAILED이다") {
                            failedDocument.status() shouldBe DocumentStatus.FAILED
                        }
                    }
                }
            }
        }
    }
})
