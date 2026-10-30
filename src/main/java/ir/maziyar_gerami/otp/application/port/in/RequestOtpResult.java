package ir.maziyar_gerami.otp.application.port.in;

public record RequestOtpResult(long expiresInSeconds, long resendAvailableInSeconds) {}
