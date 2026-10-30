package ir.maziyar_gerami.otp.adapter.in.web.requestotp;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;

public record RequestOtpRequest(
        @NotBlank @Size(max = 16) @Pattern(regexp = "\\+[1-9][0-9]{1,14}", message = "must use E.164 format")
        @Schema(example = "+989121234567") String phoneNumber,
        @Min(4) @Max(10)
        @Schema(description = "Optional OTP digit count. Defaults to otp.code-length.",
                minimum = "4", maximum = "10", example = "8") Integer codeLength) {
    public RequestOtpRequest(String phoneNumber) {
        this(phoneNumber, null);
    }

    @Override
    public String toString() { return "RequestOtpRequest[REDACTED]"; }
}
