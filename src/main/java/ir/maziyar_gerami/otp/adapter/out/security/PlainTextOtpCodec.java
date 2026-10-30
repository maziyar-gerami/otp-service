package ir.maziyar_gerami.otp.adapter.out.security;

import ir.maziyar_gerami.otp.application.port.out.OtpCodec;
import ir.maziyar_gerami.otp.domain.OtpCode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public final class PlainTextOtpCodec implements OtpCodec {
    @Override
    public String encode(OtpCode code) {
        return code.value();
    }

    @Override
    public boolean matches(OtpCode supplied, String encoded) {
        return MessageDigest.isEqual(encoded.getBytes(StandardCharsets.US_ASCII),
                supplied.value().getBytes(StandardCharsets.US_ASCII));
    }
}
