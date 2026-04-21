package com.github.psyoung16.delivery.infrastructure.external

/**
 * 외부 서류 발급 API 클라이언트
 *
 * 실제 운영에서는 정부24, 대법원 등 외부 API를 호출하지만,
 * 현재는 Mock으로 구현하여 시뮬레이션만 수행
 */
interface ExternalApiClient {

    /**
     * 1차 요청: 간편인증 요청
     *
     * @return transactionId (외부 API에서 발급한 거래 ID)
     */
    fun requestEasyAuth(
        userName: String,
        phoneNo: String,
        identity: String,
        easyAuthMethod: String
    ): String

    /**
     * 2차 요청: 인증 완료 후 실제 서류 발급
     *
     * @param transactionId 1차 요청에서 받은 거래 ID
     * @return fileUrl 발급된 서류 파일 URL
     */
    fun issueDocument(transactionId: String): String
}