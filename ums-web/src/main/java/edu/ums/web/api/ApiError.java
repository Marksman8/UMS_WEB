package edu.ums.web.api;

/** The only error shape the client ever sees. */
public record ApiError(String code, String message) {
}
