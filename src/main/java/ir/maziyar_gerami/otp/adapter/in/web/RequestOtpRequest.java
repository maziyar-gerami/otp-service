package ir.maziyar_gerami.otp.adapter.in.web;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RequestOtpRequest(
        @NotBlank @Size(max = 16) @Pattern(regexp = "\\+[1-9][0-9]{1,14}", message = "must use E.164 format")
        @Schema(example = "+989121234567") String phoneNumber) {
    @Override
    public String toString() { return "RequestOtpRequest[REDACTED]"; }
}
