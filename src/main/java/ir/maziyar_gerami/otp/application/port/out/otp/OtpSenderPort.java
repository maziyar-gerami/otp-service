package ir.maziyar_gerami.otp.application.port.out.otp;

import ir.maziyar_gerami.otp.domain.OtpCode;
import ir.maziyar_gerami.otp.domain.PhoneNumber;
import java.time.Duration;

public interface OtpSenderPort {
    void send(PhoneNumber phone, OtpCode code, Duration ttl);
}
