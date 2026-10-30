package ir.maziyar_gerami.otp.application.port.out.otp;

import ir.maziyar_gerami.otp.domain.OtpCode;

public interface OtpGeneratorPort {
    OtpCode generate(int length);
}
