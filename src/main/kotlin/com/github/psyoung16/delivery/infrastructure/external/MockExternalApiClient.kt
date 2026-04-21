package com.github.psyoung16.delivery.infrastructure.external

import org.springframework.stereotype.Component

/**
 * Mock 외부 API 클라이언트
 *
 * 실제 외부 API 호출을 시뮬레이션하는 Mock 구현
 *
 * 실제 시나리오:
 * - 1차 요청 (requestEasyAuth): 즉시 응답 (~1초 이내)
 *   카카오톡/PASS로 알림만 전송하고 transactionId 반환
 *
 * - 사용자 인증 대기: 30초 ~ 1분
 *   사용자가 카카오톡/PASS 앱에서 본인인증 수행
 *
 * - 2차 요청 (issueDocument): 10초 ~ 3분
 *   외부 API가 정부24/대법원 등에서 실제 서류 발급
 */
@Component
class MockExternalApiClient : ExternalApiClient {

    /**
     * 1차 요청: 간편인증 요청 (Mock)
     *
     * 실제 API: 카카오톡/PASS 등으로 인증 알림 전송
     * Mock: 즉시 transactionId 반환
     *
     * 실제 소요 시간: ~1초 이내
     */
    override fun requestEasyAuth(
        userName: String,
        phoneNo: String,
        identity: String,
        easyAuthMethod: String
    ): String {
        // Mock: 외부 API 호출 시뮬레이션
        Thread.sleep(500)  // 500ms 지연

        return "mock-tx-${System.currentTimeMillis()}"
    }

    /**
     * 2차 요청: 인증 완료 후 실제 서류 발급 (Mock)
     *
     * 실제 API: 정부24/대법원 등에서 서류 발급 (10초~3분 소요)
     * Mock: 실제와 유사한 지연 시뮬레이션
     *
     * 소요 시간: 10초 ~ 3분
     */
    override fun issueDocument(transactionId: String): String {
        // Mock: 외부 API 서류 발급 시뮬레이션 (10초~3분 중 최소값)
        Thread.sleep(10_000)  // 10초 지연

        return "https://external-api.com/documents/issued-${System.currentTimeMillis()}.pdf"
    }
}