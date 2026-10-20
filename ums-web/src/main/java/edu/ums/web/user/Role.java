package edu.ums.web.user;

/** The four areas of the application. */
public enum Role {
    STUDENT, FACULTY, ADMIN, DEV;

    public String authority() {
        return "ROLE_" + name();
    }
}
