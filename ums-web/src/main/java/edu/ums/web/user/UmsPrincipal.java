package edu.ums.web.user;

import java.io.Serializable;

/** Who is signed in. Stored in the HTTP session, so it stays small and serialisable. */
public record UmsPrincipal(long id, String username, String fullName, Role role) implements Serializable {
}
