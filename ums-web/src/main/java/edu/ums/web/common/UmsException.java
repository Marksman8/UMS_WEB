package edu.ums.web.common;

/**
 * The only exception type thrown on purpose by application code. {@code detail} is written by us
 * and is safe to show to the user; it never carries SQL, stack traces or other people's data.
 */
public class UmsException extends RuntimeException {

    private final ErrorCode code;

    public UmsException(ErrorCode code) {
        super(code.defaultMessage());
        this.code = code;
    }

    public UmsException(ErrorCode code, String detail) {
        super(detail == null || detail.isBlank() ? code.defaultMessage() : detail);
        this.code = code;
    }

    public UmsException(ErrorCode code, String detail, Throwable cause) {
        super(detail == null || detail.isBlank() ? code.defaultMessage() : detail, cause);
        this.code = code;
    }

    public ErrorCode code() {
        return code;
    }
}
