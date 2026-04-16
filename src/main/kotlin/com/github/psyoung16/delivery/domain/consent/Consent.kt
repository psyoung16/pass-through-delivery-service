package com.github.psyoung16.delivery.domain.consent

import com.github.psyoung16.delivery.domain.member.MemberId
import jakarta.persistence.*
import java.time.Instant

/**
 * 재개발/재건축 서류 발급 동의서
 *
 * @property id 엔티티 식별자
 * @property memberId 조합원 식별자
 * @property type 동의 방식 (전자/종이)
 * @property status 동의서 상태 (작성 중/제출 완료)
 * @property submittedAt 제출 일시
 */
@Entity
@Table(name = "consents")
data class Consent(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    val memberId: Long,

    @Enumerated(EnumType.STRING)
    val type: ConsentType,

    @Enumerated(EnumType.STRING)
    val status: ConsentStatus = ConsentStatus.DRAFT,

    val submittedAt: Instant? = null
) {
    /**
     * 도메인 식별자
     */
    fun consentId(): ConsentId = ConsentId(id)

    /**
     * 조합원 도메인 식별자
     */
    fun memberDomainId(): MemberId = MemberId(memberId)

    /**
     * 동의서 제출
     */
    fun submit(): Consent {
        require(status == ConsentStatus.DRAFT) { "Only draft consents can be submitted" }
        return copy(
            status = ConsentStatus.SUBMITTED,
            submittedAt = Instant.now()
        )
    }
}

