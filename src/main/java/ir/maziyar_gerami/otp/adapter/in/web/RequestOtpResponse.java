package ir.maziyar_gerami.otp.adapter.in.web;

public record RequestOtpResponse(long expiresInSeconds, long resendAvailableInSeconds) {}
