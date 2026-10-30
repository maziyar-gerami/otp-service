package ir.maziyar_gerami.otp.adapter.in.web;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

public record VerifyOtpRequest(
        @NotBlank @Size(max = 16) @Pattern(regexp = "\\+[1-9][0-9]{1,14}", message = "must use E.164 format")
        @Schema(example = "+989121234567") String phoneNumber,
        @NotBlank @Pattern(regexp = "[0-9]{4,10}", message = "must contain 4 to 10 ASCII digits")
        @Schema(example = "123456") String code) {
    @Override
    public String toString() { return "VerifyOtpRequest[REDACTED]"; }
}
