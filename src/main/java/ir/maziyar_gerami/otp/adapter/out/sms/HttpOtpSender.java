package ir.maziyar_gerami.otp.adapter.out.sms;

import ir.maziyar_gerami.otp.application.port.out.OtpSenderPort;
import ir.maziyar_gerami.otp.domain.*;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

public final class HttpOtpSender implements OtpSenderPort {
    private static final Logger log = LoggerFactory.getLogger(HttpOtpSender.class);
    private final RestClient client;
    private final String sendPath;

    public HttpOtpSender(RestClient client, String sendPath) {
        this.client = client;
        this.sendPath = sendPath;
    }

    @Override
    public void send(PhoneNumber phone, OtpCode code, Duration ttl) {
        try {
            var response = client.post().uri(sendPath)
                    .body(new SmsPayload(phone.value(), code.value(), ttl.toSeconds()))
                    .retrieve().toBodilessEntity();
            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new RestClientException("SMS provider did not accept delivery.");
            }
            log.info("SMS provider accepted OTP delivery phone={}", phone.masked());
        } catch (RestClientException exception) {
            // Provider responses and exceptions can echo secrets; never include them in logs or API errors.
            log.warn("SMS provider failed OTP delivery phone={}", phone.masked());
            throw new OtpException(ErrorCode.OTP_DELIVERY_FAILED, "OTP delivery failed. Request a new OTP.");
        }
    }

    private record SmsPayload(String phoneNumber, String code, long expiresInSeconds) {
        @Override
        public String toString() {
            return "SmsPayload[REDACTED]";
        }
    }
}
