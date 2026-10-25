package cn.campus.resource.reservation;

import jakarta.validation.ConstraintViolationException;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.transaction.TransactionTimedOutException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
class ApiExceptionHandler {
    private static final Logger LOG = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler({CannotAcquireLockException.class, QueryTimeoutException.class, TransactionTimedOutException.class})
    ResponseEntity<Map<String, String>> busy(Exception ignored) {
        return failure(HttpStatus.SERVICE_UNAVAILABLE, "RESERVATION_BUSY", "预约服务繁忙，请稍后重试");
    }

    @ExceptionHandler(ApiFailure.class)
    ResponseEntity<Map<String, String>> domain(ApiFailure failure) {
        return ResponseEntity.status(failure.status()).body(ApiErrors.body(failure.code(), failure.getMessage()));
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, ConstraintViolationException.class,
            HandlerMethodValidationException.class, HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class, MissingServletRequestParameterException.class})
    ResponseEntity<Map<String, String>> invalid(Exception ignored) {
        return failure(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "请求参数不符合要求");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<Map<String, String>> conflict(DataIntegrityViolationException ignored) {
        return failure(HttpStatus.CONFLICT, "DATA_CONFLICT", "数据与现有记录冲突");
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<Map<String, String>> forbidden(AccessDeniedException ignored) {
        return failure(HttpStatus.FORBIDDEN, "FORBIDDEN", "无权执行此操作");
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<Map<String, String>> missing(NoResourceFoundException ignored) {
        return failure(HttpStatus.NOT_FOUND, "NOT_FOUND", "请求的资源不存在");
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<Map<String, String>> method(HttpRequestMethodNotSupportedException ignored) {
        return failure(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED", "请求方法不受支持");
    }

    @ExceptionHandler(DataAccessResourceFailureException.class)
    ResponseEntity<Map<String, String>> unavailable(DataAccessResourceFailureException ignored) {
        return failure(HttpStatus.SERVICE_UNAVAILABLE, "DATABASE_UNAVAILABLE", "数据库暂不可用");
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Map<String, String>> unexpected(Exception error) {
        // Exception messages may contain SQL parameters; keep only its type and trace ID.
        LOG.error("requestId={} failureType={}", RequestIds.current(), error.getClass().getSimpleName());
        return failure(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "服务暂时无法完成请求");
    }

    private ResponseEntity<Map<String, String>> failure(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(ApiErrors.body(code, message));
    }
}
