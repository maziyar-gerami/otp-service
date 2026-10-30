package ir.maziyar_gerami.otp.adapter.in.web;

import ir.maziyar_gerami.otp.adapter.in.web.requestotp.RequestOtpRequest;
import ir.maziyar_gerami.otp.adapter.in.web.requestotp.RequestOtpResponse;
import ir.maziyar_gerami.otp.adapter.in.web.verifyotp.VerifyOtpRequest;
import ir.maziyar_gerami.otp.adapter.in.web.verifyotp.VerifyOtpResponse;
import ir.maziyar_gerami.otp.application.port.in.requestotp.RequestOtpUseCase;
import ir.maziyar_gerami.otp.application.port.in.verifyotp.VerifyOtpUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import org.springframework.http.ProblemDetail;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/otp")
@ApiResponses({
        @ApiResponse(responseCode = "400", description = "Validation or OTP verification failure",
                content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(responseCode = "429", description = "Rate limit or cooldown; Retry-After is seconds",
                content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(responseCode = "503", description = "Redis or SMS unavailable",
                content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
})
public class OtpController {
    private final RequestOtpUseCase requestOtp;
    private final VerifyOtpUseCase verifyOtp;

    public OtpController(RequestOtpUseCase requestOtp, VerifyOtpUseCase verifyOtp) {
        this.requestOtp = requestOtp;
        this.verifyOtp = verifyOtp;
    }

    @PostMapping("/request")
    @Operation(summary = "Request an OTP", description = "Sends SMS in production; returns code without SMS in dev.")
    public ResponseEntity<RequestOtpResponse> request(@Valid @RequestBody RequestOtpRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(OtpWebMapper.response(requestOtp.request(OtpWebMapper.command(request))));
    }

    @PostMapping("/verify")
    @Operation(summary = "Verify and immediately consume an OTP")
    public VerifyOtpResponse verify(@Valid @RequestBody VerifyOtpRequest request) {
        return OtpWebMapper.response(verifyOtp.verify(OtpWebMapper.command(request)));
    }
}
