package ir.maziyar_gerami.otp.adapter.out.security;

import ir.maziyar_gerami.otp.application.port.out.otp.OtpGeneratorPort;
import ir.maziyar_gerami.otp.domain.OtpCode;
import java.security.SecureRandom;

public final class SecureRandomOtpGenerator implements OtpGeneratorPort {
    private final SecureRandom random;

    public SecureRandomOtpGenerator(SecureRandom random) {
        this.random = random;
    }

    @Override
    public OtpCode generate(int length) {
        if (length < 4 || length > 10) {
            throw new IllegalArgumentException("OTP length must be between 4 and 10.");
        }
        var code = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            code.append(random.nextInt(10));
        }
        return new OtpCode(code.toString());
    }
}
