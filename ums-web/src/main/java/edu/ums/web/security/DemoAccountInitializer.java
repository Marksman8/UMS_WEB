package edu.ums.web.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * The demo migration stores the placeholder hash '{demo}'. On start-up this replaces it with a real
 * BCrypt hash of the configured demo password, so no password hash is ever committed to the repo.
 */
@Component
public class DemoAccountInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoAccountInitializer.class);

    private final JdbcTemplate jdbc;
    private final PasswordEncoder encoder;
    private final String demoPassword;

    public DemoAccountInitializer(JdbcTemplate jdbc, PasswordEncoder encoder, @Value("${ums.demo-password}") String demoPassword) {
        this.jdbc = jdbc;
        this.encoder = encoder;
        this.demoPassword = demoPassword;
    }

    @Override
    public void run(ApplicationArguments args) {
        Integer pending = jdbc.queryForObject("SELECT count(*) FROM app_user WHERE password_hash = '{demo}'", Integer.class);
        if (pending == null || pending == 0) {
            return;
        }
        int updated = jdbc.update("UPDATE app_user SET password_hash = ? WHERE password_hash = '{demo}'", encoder.encode(demoPassword));
        log.info("Demo accounts ready: {} users can sign in with the configured demo password", updated);
    }
}
