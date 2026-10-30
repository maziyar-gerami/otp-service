package ir.maziyar_gerami.otp.application.port.in.requestotp;

import ir.maziyar_gerami.otp.domain.OtpCode;

public record RequestOtpResult(long expiresInSeconds, long resendAvailableInSeconds, OtpCode code) {
    public RequestOtpResult(long expiresInSeconds, long resendAvailableInSeconds) {
        this(expiresInSeconds, resendAvailableInSeconds, null);
    }
}
