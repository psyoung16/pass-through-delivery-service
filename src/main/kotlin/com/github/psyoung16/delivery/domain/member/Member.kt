package com.github.psyoung16.delivery.domain.member

import jakarta.persistence.*

/**
 * 재개발/재건축 조합원
 */
@Entity
@Table(name = "members")
data class Member(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    val name: String,

    val residentNumber: String,

    @Embedded
    val contactInfo: ContactInfo,

    @ElementCollection
    @CollectionTable(name = "member_property_rights", joinColumns = [JoinColumn(name = "member_id")])
    val propertyRights: List<PropertyRight> = emptyList()
) {
    init {
        require(name.isNotBlank()) { "Name must not be blank" }
        require(residentNumber.matches(Regex("\\d{6}-\\d{7}"))) {
            "Resident number must be in format ######-#######"
        }
    }

    /**
     * 도메인 식별자
     */
    fun memberId(): MemberId = MemberId(id)
}