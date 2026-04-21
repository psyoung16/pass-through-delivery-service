package com.github.psyoung16.delivery.infrastructure.external

import org.springframework.stereotype.Component

/**
 * Mock 외부 API 클라이언트
 *
 * 실제 외부 API 호출을 시뮬레이션하는 Mock 구현
 * 지연 시간을 포함하여 실제 API처럼 동작
 */
@Component
class MockExternalApiClient : ExternalApiClient {

    /**
     * 1차 요청: 간편인증 요청 (Mock)
     *
     * 실제 API: 카카오톡/PASS 등으로 인증 요청 전송
     * Mock: 즉시 transactionId 반환
     */
    override fun requestEasyAuth(
        userName: String,
        phoneNo: String,
        identity: String,
        easyAuthMethod: String
    ): String {
        // Mock: 외부 API 호출 시뮬레이션
        Thread.sleep(50)  // 50ms 지연

        return "mock-tx-${System.currentTimeMillis()}"
    }

    /**
     * 2차 요청: 인증 완료 후 실제 서류 발급 (Mock)
     *
     * 실제 API: 사용자가 카카오톡에서 인증 완료 후 서류 발급
     * Mock: 약간의 지연 후 fileUrl 반환
     */
    override fun issueDocument(transactionId: String): String {
        // Mock: 외부 API 서류 발급 시뮬레이션 (1~3초 소요)
        Thread.sleep(100)  // 100ms 지연

        return "https://external-api.com/documents/issued-${System.currentTimeMillis()}.pdf"
    }
}