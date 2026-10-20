package edu.ums.web.user;

import edu.ums.web.common.ErrorCode;
import edu.ums.web.common.UmsException;
import java.util.Optional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * The only way services learn who is calling. Student and faculty endpoints never accept a user id
 * from the client; they always ask here.
 */
@Component
public class CurrentUser {

    public Optional<UmsPrincipal> find() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof UmsPrincipal p) {
            return Optional.of(p);
        }
        return Optional.empty();
    }

    public UmsPrincipal require() {
        return find().orElseThrow(() -> new UmsException(ErrorCode.UNAUTHENTICATED));
    }

    public long id() {
        return require().id();
    }
}
