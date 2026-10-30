package ir.maziyar_gerami.otp.application.port.out;

import ir.maziyar_gerami.otp.domain.OtpCode;

public interface OtpGeneratorPort {
    OtpCode generate(int length);
}
