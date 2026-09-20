package com.jasonwidjaja.dvp.api;

import org.springframework.core.convert.ConversionFailedException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
class ApiExceptionHandler {

    @ExceptionHandler(InvalidIdempotencyKeyException.class)
    ResponseEntity<ErrorResponse> invalidIdempotencyKey(InvalidIdempotencyKeyException ex) {
        return error(400, "INVALID_IDEMPOTENCY_KEY", ex.getMessage());
    }

    @ExceptionHandler(UnknownTradeException.class)
    ResponseEntity<ErrorResponse> unknownTrade() {
        return error(404, "UNKNOWN_TRADE", "Trade does not exist");
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ErrorResponse> malformedRequest() {
        return error(400, "MALFORMED_REQUEST", "Request body is malformed or has an invalid value");
    }

    @ExceptionHandler({
            MethodArgumentNotValidException.class,
            HandlerMethodValidationException.class,
            MethodArgumentTypeMismatchException.class,
            ConversionFailedException.class
    })
    ResponseEntity<ErrorResponse> invalidRequest() {
        return error(400, "INVALID_REQUEST", "Request is structurally invalid");
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ResponseEntity<ErrorResponse> unsupportedMediaType() {
        return error(415, "UNSUPPORTED_MEDIA_TYPE", "Content-Type must be application/json");
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ErrorResponse> unexpectedFailure() {
        return error(500, "INTERNAL_ERROR", "An unexpected error occurred");
    }

    private static ResponseEntity<ErrorResponse> error(int status, String code, String message) {
        return ResponseEntity.status(status).body(new ErrorResponse(code, message));
    }
}
