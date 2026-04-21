package com.github.psyoung16.delivery.infrastructure.external

import org.springframework.stereotype.Component

/**
 * 정부24 API 클라이언트 (주민등록등본 발급)
 *
 * 실제 시나리오:
 * - 1차 요청 (requestEasyAuth): 카카오톡/PASS로 본인인증 요청 (~500ms)
 * - 2차 요청 (issueDocument): 정부24에서 주민등록등본 발급 (10초~3분)
 */
@Component
class Gov24ApiClient : ExternalApiClient {

    /**
     * 1차 요청: 간편인증 요청 (Mock)
     *
     * 실제 API: 카카오톡/PASS 등으로 인증 알림 전송
     * Mock: 즉시 transactionId 반환
     *
     * 실제 소요 시간: ~500ms
     */
    override fun requestEasyAuth(
        userName: String,
        phoneNo: String,
        identity: String,
        easyAuthMethod: String
    ): String {
        // Mock: 정부24 API 호출 시뮬레이션
        Thread.sleep(500)  // 500ms 지연

        return "gov24-tx-${System.currentTimeMillis()}"
    }

    /**
     * 2차 요청: 인증 완료 후 주민등록등본 발급 (Mock)
     *
     * 실제 API: 정부24에서 주민등록등본 발급 (10초~3분 소요)
     * Mock: 실제와 유사한 지연 시뮬레이션
     *
     * 소요 시간: 3초 (Mock 간소화)
     */
    override fun issueDocument(transactionId: String): String {
        // Mock: 정부24 서류 발급 시뮬레이션 (3초)
        Thread.sleep(3_000)  // 3초 지연

        return "https://gov24.go.kr/documents/resident-${System.currentTimeMillis()}.pdf"
    }
}