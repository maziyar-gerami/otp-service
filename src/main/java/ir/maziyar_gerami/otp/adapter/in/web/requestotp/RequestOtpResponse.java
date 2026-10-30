package ir.maziyar_gerami.otp.adapter.in.web.requestotp;

public record RequestOtpResponse(long expiresInSeconds, long resendAvailableInSeconds) {}
