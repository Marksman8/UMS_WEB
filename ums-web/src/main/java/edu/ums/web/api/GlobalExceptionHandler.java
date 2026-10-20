package edu.ums.web.api;

import edu.ums.web.audit.AuditService;
import edu.ums.web.common.ErrorCode;
import edu.ums.web.common.UmsException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/** Turns every failure into {code, message}. Stack traces and SQL stay in the server log. */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final Pattern CONSTRAINT = Pattern.compile("constraint \"([^\"]+)\"");

    private static final Map<String, String> FRIENDLY = Map.ofEntries(
            Map.entry("uq_app_user_username", "That username is already taken."),
            Map.entry("uq_department_code", "A department with that code already exists."),
            Map.entry("uq_department_name", "A department with that name already exists."),
            Map.entry("uq_programme_code", "A programme with that code already exists."),
            Map.entry("uq_student_roll", "That roll number is already used."),
            Map.entry("uq_course_code", "A course with that code already exists."),
            Map.entry("uq_term_name", "A term with that name already exists."),
            Map.entry("uq_section_identity", "That section already exists for this course and term."),
            Map.entry("uq_enrolment_live", "You are already registered in this section."),
            Map.entry("uq_session_day", "A class session already exists for that date."));

    private final AuditService audit;

    public GlobalExceptionHandler(AuditService audit) {
        this.audit = audit;
    }

    @ExceptionHandler(UmsException.class)
    public ResponseEntity<ApiError> handleUms(UmsException e) {
        return ResponseEntity.status(e.code().status()).body(new ApiError(e.code().name(), e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream().findFirst()
                .map(f -> f.getField() + ": " + f.getDefaultMessage()).orElse(ErrorCode.VALIDATION.defaultMessage());
        return build(ErrorCode.VALIDATION, msg);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ApiError> handleMalformed(Exception e) {
        return build(ErrorCode.VALIDATION, "The request could not be understood.");
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleDenied(AccessDeniedException e, HttpServletRequest request) {
        audit.denied(request.getMethod() + " " + request.getRequestURI());
        return build(ErrorCode.FORBIDDEN, null);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiError> handleNoResource(NoResourceFoundException e) {
        return build(ErrorCode.NOT_FOUND, null);
    }

    @ExceptionHandler(DuplicateKeyException.class)
    public ResponseEntity<ApiError> handleDuplicate(DuplicateKeyException e) {
        String name = constraintName(e);
        return build(ErrorCode.DUPLICATE_VALUE, name == null ? null : FRIENDLY.get(name));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleIntegrity(DataIntegrityViolationException e) {
        String name = constraintName(e);
        log.info("Constraint violation: {}", name);
        return switch (name == null ? "" : name) {
            case "ck_section_capacity" -> build(ErrorCode.SECTION_FULL, null);
            case "ck_mark_nonneg" -> build(ErrorCode.MARK_OUT_OF_RANGE, "Marks cannot be negative.");
            default -> build(ErrorCode.CONSTRAINT_VIOLATION, name != null && name.startsWith("fk_")
                    ? "A related record is missing or still in use." : null);
        };
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleOther(Exception e) {
        log.error("Unhandled error", e);
        return build(ErrorCode.INTERNAL, null);
    }

    private static String constraintName(Throwable e) {
        Throwable root = e;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        String msg = root.getMessage();
        if (msg == null) {
            return null;
        }
        Matcher m = CONSTRAINT.matcher(msg);
        return m.find() ? m.group(1) : null;
    }

    private static ResponseEntity<ApiError> build(ErrorCode code, String message) {
        return ResponseEntity.status(code.status()).body(new ApiError(code.name(), message == null ? code.defaultMessage() : message));
    }
}
