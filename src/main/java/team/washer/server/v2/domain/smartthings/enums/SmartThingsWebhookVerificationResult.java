package team.washer.server.v2.domain.smartthings.enums;

/**
 * SmartThings webhook HTTP Signature 검증 결과. 거부 원인을 로그로 구분하기 위해 예외 대신 결과 코드를 쓴다.
 */
public enum SmartThingsWebhookVerificationResult {
    VALID, MISSING_SIGNATURE, UNSUPPORTED_ALGORITHM, MISSING_DIGEST, UNSUPPORTED_DIGEST, DIGEST_MISMATCH, MISSING_DATE, INVALID_DATE, DATE_SKEW, REQUIRED_HEADER_NOT_SIGNED, MISSING_SIGNED_HEADER, KEY_ID_NOT_ALLOWED, KEY_UNAVAILABLE, SIGNATURE_MISMATCH;

    public boolean isValid() {
        return this == VALID;
    }
}
