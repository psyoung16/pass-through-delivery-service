package com.github.psyoung16.delivery.domain.document

/**
 * 서류 발급 실패 타입
 */
enum class FailureType(val description: String) {
    /**
     * 재시도 가능한 실패
     *
     * 예시:
     * - "외부 기관 시스템 점검 중입니다"
     * - "일시적인 네트워크 오류"
     * - "서버 응답 시간 초과"
     */
    RETRYABLE("재시도 가능"),

    /**
     * 영구적 실패 (데이터 수정 또는 처음부터 다시 필요)
     *
     * 예시:
     * - "주민등록번호가 일치하지 않습니다"
     * - "발급 대상이 아닙니다"
     * - "등본 주소지와 신청 주소지가 다릅니다"
     * - "자격 요건을 충족하지 않습니다"
     */
    PERMANENT("영구적 실패")
}