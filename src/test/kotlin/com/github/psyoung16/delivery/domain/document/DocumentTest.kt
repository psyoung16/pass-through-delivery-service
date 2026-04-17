package com.github.psyoung16.delivery.domain.document

import com.github.psyoung16.delivery.domain.consent.ConsentId
import com.github.psyoung16.delivery.domain.document.events.DocumentRequested
import com.github.psyoung16.delivery.domain.document.events.DocumentUploaded
import com.github.psyoung16.delivery.domain.document.events.TwoWayAuthRequired
import com.github.psyoung16.delivery.domain.member.MemberId
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

class DocumentTest : DescribeSpec({

    describe("유저가 직접 서류를 첨부하면") {
        val documentId = DocumentId(1L)
        val consentId = ConsentId(100L)
        val memberId = MemberId(200L)
        val documentType = DocumentType.RESIDENT_REGISTRATION
        val issuanceMethod = IssuanceMethod.USER_UPLOADED

        val (document, _) = Document.create(
            id = documentId,
            consentId = consentId,
            memberId = memberId,
            documentType = documentType,
            issuanceMethod = issuanceMethod
        )

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

    describe("서류 발급을 요청하면") {
        val documentId = DocumentId(1L)
        val consentId = ConsentId(100L)
        val memberId = MemberId(200L)
        val documentType = DocumentType.RESIDENT_REGISTRATION
        val issuanceMethod = IssuanceMethod.API_ISSUED

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
    }

    describe("서류 발급 요청 후 외부 기관에서 2차 인증을 요구하면") {
        val documentId = DocumentId(1L)
        val consentId = ConsentId(100L)
        val memberId = MemberId(200L)
        val documentType = DocumentType.RESIDENT_REGISTRATION
        val issuanceMethod = IssuanceMethod.API_ISSUED

        val (document, _) = Document.create(
            id = documentId,
            consentId = consentId,
            memberId = memberId,
            documentType = documentType,
            issuanceMethod = issuanceMethod
        )

        val reason = "본인 인증 필요"
        val (updatedDocument, authEvent) = document.requireTwoWayAuth(reason)

        it("TwoWayAuthRequired 이벤트가 발행된다") {
            authEvent.shouldBeInstanceOf<TwoWayAuthRequired>()
            authEvent.documentId shouldBe documentId
            authEvent.reason shouldBe reason
        }

        it("상태는 TWO_WAY_AUTH_REQUIRED이다") {
            updatedDocument.status() shouldBe DocumentStatus.TWO_WAY_AUTH_REQUIRED
        }
    }
})
