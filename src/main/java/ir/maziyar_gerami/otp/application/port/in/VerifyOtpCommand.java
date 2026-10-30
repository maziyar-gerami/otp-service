package ir.maziyar_gerami.otp.application.port.in;

import ir.maziyar_gerami.otp.domain.OtpCode;
import ir.maziyar_gerami.otp.domain.PhoneNumber;

public record VerifyOtpCommand(PhoneNumber phoneNumber, OtpCode code) {}
