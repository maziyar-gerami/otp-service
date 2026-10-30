package ir.maziyar_gerami.otp.application.port.out;

import ir.maziyar_gerami.otp.domain.OtpCode;

public interface OtpCodec {
    String encode(OtpCode code);
    boolean matches(OtpCode supplied, String encoded);
}
