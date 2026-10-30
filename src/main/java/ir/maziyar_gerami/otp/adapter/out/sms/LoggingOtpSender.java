package ir.maziyar_gerami.otp.adapter.out.sms;

import ir.maziyar_gerami.otp.application.port.out.OtpSenderPort;
import ir.maziyar_gerami.otp.domain.OtpCode;
import ir.maziyar_gerami.otp.domain.PhoneNumber;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// Wired exclusively in the dev profile. Never enable dev in production.
public final class LoggingOtpSender implements OtpSenderPort {
    private static final Logger log = LoggerFactory.getLogger(LoggingOtpSender.class);

    @Override
    public void send(PhoneNumber phone, OtpCode code, Duration ttl) {
        log.info("DEV ONLY OTP phone={} code={} expiresInSeconds={}", phone.masked(), code.value(), ttl.toSeconds());
    }
}
