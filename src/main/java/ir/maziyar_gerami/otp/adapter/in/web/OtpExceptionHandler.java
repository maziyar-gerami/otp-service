package ir.maziyar_gerami.otp.adapter.in.web;

import ir.maziyar_gerami.otp.domain.ErrorCode;
import ir.maziyar_gerami.otp.domain.OtpException;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class OtpExceptionHandler extends ResponseEntityExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(OtpExceptionHandler.class);

    @ExceptionHandler(OtpException.class)
    public ResponseEntity<ProblemDetail> otp(OtpException exception, HttpServletRequest request) {
        HttpStatus status = switch (exception.code()) {
            case OTP_RATE_LIMITED, OTP_COOLDOWN_ACTIVE -> HttpStatus.TOO_MANY_REQUESTS;
            case OTP_DELIVERY_FAILED, OTP_SERVICE_UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
            default -> HttpStatus.BAD_REQUEST;
        };
        var headers = new HttpHeaders();
        if (status == HttpStatus.TOO_MANY_REQUESTS) {
            headers.set(HttpHeaders.RETRY_AFTER, Long.toString(Math.max(1, exception.retryAfterSeconds())));
        }
        headers.setCacheControl("no-store");
        var detail = problem(exception.code(), status, exception.getMessage());
        detail.setInstance(URI.create(request.getRequestURI()));
        return new ResponseEntity<>(detail, headers, status);
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ProblemDetail> storage(DataAccessException exception) {
        log.error("OTP storage unavailable exceptionType={}", exception.getClass().getSimpleName());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).cacheControl(CacheControl.noStore())
                .body(problem(ErrorCode.OTP_SERVICE_UNAVAILABLE, HttpStatus.SERVICE_UNAVAILABLE,
                        "OTP storage is temporarily unavailable."));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> unexpected(Exception exception) {
        log.error("Unexpected OTP failure exceptionType={}", exception.getClass().getSimpleName());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).cacheControl(CacheControl.noStore())
                .body(problem(ErrorCode.OTP_SERVICE_UNAVAILABLE, HttpStatus.INTERNAL_SERVER_ERROR,
                        "The service could not complete the request."));
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException exception,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        var detail = problem(ErrorCode.VALIDATION_ERROR, HttpStatus.BAD_REQUEST, "Request validation failed.");
        detail.setProperty("errors", exception.getBindingResult().getFieldErrors().stream()
                .map(error -> Map.of("field", error.getField(), "message",
                        error.getDefaultMessage() == null ? "Invalid value." : error.getDefaultMessage())).toList());
        return handleExceptionInternal(exception, detail, headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception exception, Object body,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        Object response = body;
        if (body == null || body instanceof ProblemDetail) {
            var detail = body instanceof ProblemDetail p ? p : ProblemDetail.forStatus(status);
            detail.setProperty("code", ErrorCode.VALIDATION_ERROR.name());
            detail.setType(URI.create("urn:otp:error:VALIDATION_ERROR"));
            detail.setDetail(status.value() == 400 ? "Request validation failed." : "HTTP request could not be processed.");
            response = detail;
        }
        var safeHeaders = new HttpHeaders();
        safeHeaders.putAll(headers);
        safeHeaders.setCacheControl("no-store");
        return super.handleExceptionInternal(exception, response, safeHeaders, status, request);
    }

    private static ProblemDetail problem(ErrorCode code, HttpStatus status, String message) {
        var detail = ProblemDetail.forStatusAndDetail(status, message);
        detail.setType(URI.create("urn:otp:error:" + code.name()));
        detail.setTitle(code.name());
        detail.setProperty("code", code.name());
        return detail;
    }
}
