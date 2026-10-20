package edu.ums.web.common;

import org.springframework.http.HttpStatus;

/** Every error the API can return. The client receives {code, message}; nothing else. */
public enum ErrorCode {
    VALIDATION(HttpStatus.BAD_REQUEST, "Please check the values you entered."),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "Wrong username or password."),
    ACCOUNT_LOCKED(HttpStatus.LOCKED, "Too many failed attempts. Try again in 15 minutes."),
    ACCOUNT_DISABLED(HttpStatus.FORBIDDEN, "This account is disabled. Contact the administrator."),
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "Please sign in."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "You are not allowed to do that."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "That record does not exist."),
    DUPLICATE_VALUE(HttpStatus.CONFLICT, "That value already exists."),
    STALE_DATA(HttpStatus.CONFLICT, "Someone else changed this record. Reload and try again."),
    CONSTRAINT_VIOLATION(HttpStatus.CONFLICT, "The data breaks a database rule."),
    TERM_STATE(HttpStatus.CONFLICT, "That is not allowed in the term's current stage."),
    SECTION_FULL(HttpStatus.CONFLICT, "No seats left in this section."),
    ALREADY_ENROLLED(HttpStatus.CONFLICT, "You are already registered for this course in this term."),
    TIME_CLASH(HttpStatus.CONFLICT, "This section overlaps another course you registered."),
    PREREQ_MISSING(HttpStatus.CONFLICT, "Prerequisite courses are not completed."),
    CREDIT_LIMIT(HttpStatus.CONFLICT, "This would exceed the credit limit for the term."),
    SHEET_STATE(HttpStatus.CONFLICT, "The mark sheet is not in a state that allows this."),
    SHEETS_PENDING(HttpStatus.CONFLICT, "Some mark sheets are not approved yet."),
    MARKS_INCOMPLETE(HttpStatus.CONFLICT, "Every student needs a mark for every component before submitting."),
    MARK_OUT_OF_RANGE(HttpStatus.BAD_REQUEST, "A mark is outside the allowed range."),
    SQL_REJECTED(HttpStatus.BAD_REQUEST, "Only a single read-only SELECT query is allowed."),
    SQL_FAILED(HttpStatus.BAD_REQUEST, "The query could not be executed."),
    AI_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "The AI service is not reachable right now."),
    INTERNAL(HttpStatus.INTERNAL_SERVER_ERROR, "Something went wrong on our side.");

    private final HttpStatus status;
    private final String defaultMessage;

    ErrorCode(HttpStatus status, String defaultMessage) {
        this.status = status;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus status() {
        return status;
    }

    public String defaultMessage() {
        return defaultMessage;
    }
}
