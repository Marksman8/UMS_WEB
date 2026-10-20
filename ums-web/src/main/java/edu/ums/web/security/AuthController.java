package edu.ums.web.security;

import edu.ums.web.audit.AuditService;
import edu.ums.web.user.CurrentUser;
import edu.ums.web.user.UmsPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.HttpStatus;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final SecurityContextRepository contextRepository;
    private final CurrentUser currentUser;
    private final AuditService audit;

    public AuthController(AuthService authService, SecurityContextRepository contextRepository,
                          CurrentUser currentUser, AuditService audit) {
        this.authService = authService;
        this.contextRepository = contextRepository;
        this.currentUser = currentUser;
        this.audit = audit;
    }

    @PostMapping("/login")
    public Me login(@Valid @RequestBody LoginRequest body, HttpServletRequest request, HttpServletResponse response) {
        UmsPrincipal principal = authService.login(body.username().trim(), body.password());
        var auth = UsernamePasswordAuthenticationToken.authenticated(principal, null,
                List.of(new SimpleGrantedAuthority(principal.role().authority())));
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(auth);
        SecurityContextHolder.setContext(context);
        if (request.getSession(false) != null) {
            request.changeSessionId(); // new session id after login (fixation defence)
        }
        contextRepository.saveContext(context, request, response);
        return Me.of(principal);
    }

    @GetMapping("/me")
    public Me me() {
        return Me.of(currentUser.require());
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpServletRequest request, HttpServletResponse response) {
        currentUser.find().ifPresent(p -> audit.record("LOGOUT", "app_user", p.id(), null));
        new SecurityContextLogoutHandler().logout(request, response, SecurityContextHolder.getContext().getAuthentication());
    }

    public record LoginRequest(@NotBlank @Size(max = 40) String username, @NotBlank @Size(max = 100) String password) {
    }

    public record Me(long id, String username, String fullName, String role) {
        static Me of(UmsPrincipal p) {
            return new Me(p.id(), p.username(), p.fullName(), p.role().name());
        }
    }
}
