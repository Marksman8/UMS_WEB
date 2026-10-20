package edu.ums.web.ai;

import edu.ums.web.user.Role;
import edu.ums.web.user.UmsPrincipal;
import java.util.List;

/**
 * Each area of the app describes, in plain text, the live data its users are allowed to ask about.
 * The assistant only ever sees what the signed-in person's own contributor returns.
 */
public interface AiContextContributor {

    Role role();

    List<Fact> facts(UmsPrincipal who);

    /** One topic: the {@code keywords} route a question to it in local mode; {@code text} is what gets said (and sent to Claude). */
    record Fact(String key, List<String> keywords, String text) {
    }
}
