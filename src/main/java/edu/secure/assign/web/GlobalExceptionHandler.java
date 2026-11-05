package edu.secure.assign.web;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log =
            LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleStatus(
            ResponseStatusException e) {

        int status = e.getStatusCode().value();

        return ResponseEntity.status(status).body(
            Map.of(
                "error", status == 400 ? "BAD_REQUEST" : "ERROR",
                "message", "요청을 처리할 수 없습니다."
            )
        );
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgument(
            IllegalArgumentException e) {

        return ResponseEntity.badRequest().body(
            Map.of(
                "error", "BAD_REQUEST",
                "message", "입력값이 올바르지 않습니다."
            )
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handle(Exception e) {

        log.error("서버 내부 오류 발생", e);

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
            Map.of(
                "error", "INTERNAL_SERVER_ERROR",
                "message", "서버 내부 오류가 발생했습니다."
            )
        );
    }
}