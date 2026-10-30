package ir.maziyar_gerami.otp.adapter.in.web.requestotp;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record RequestOtpResponse(long expiresInSeconds, long resendAvailableInSeconds,
                                @Schema(description = "Exact OTP code, present only in dev without prod.",
                                        example = "001234") String code) {
    @Override
    public String toString() {
        return "RequestOtpResponse[expiresInSeconds=" + expiresInSeconds
                + ", resendAvailableInSeconds=" + resendAvailableInSeconds + ", code=[REDACTED]]";
    }
}
