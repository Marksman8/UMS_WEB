package edu.ums.web.security;

import edu.ums.web.user.UmsPrincipal;

public interface AuthService {

    /** Verifies the credentials, applies the lockout rule and returns who signed in. */
    UmsPrincipal login(String username, String password);
}
