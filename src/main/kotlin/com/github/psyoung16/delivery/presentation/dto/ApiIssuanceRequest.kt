package com.github.psyoung16.delivery.presentation.dto

import com.github.psyoung16.delivery.domain.document.DocumentType

/**
 * API 자동 발급 요청 (2-way 인증 시작)
 */
data class ApiIssuanceRequest(
    val consentId: Long,
    val memberId: Long,
    val documentType: DocumentType,
    val userName: String,
    val phoneNo: String,
    val identity: String,  // 주민번호 (암호화)
    val easyAuthMethod: String  // KAKAO, PASS, NAVER 등
)