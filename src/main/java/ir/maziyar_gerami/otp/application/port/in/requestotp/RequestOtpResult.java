package ir.maziyar_gerami.otp.application.port.in.requestotp;

public record RequestOtpResult(long expiresInSeconds, long resendAvailableInSeconds) {}
