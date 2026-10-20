package edu.ums.web.ai;

import edu.ums.web.user.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The assistant is available to every signed-in role; what it knows depends on the caller's role. */
@RestController
@RequestMapping("/api/ai")
public class AiController {

    private final AiChatService chat;
    private final CurrentUser currentUser;

    public AiController(AiChatService chat, CurrentUser currentUser) {
        this.chat = chat;
        this.currentUser = currentUser;
    }

    @PostMapping("/chat")
    public AiChatService.Reply chat(@Valid @RequestBody AiChatService.ChatRequest body) {
        return chat.chat(currentUser.require(), body.message(), body.history());
    }

    @GetMapping("/status")
    public AiChatService.Status status() {
        return chat.status();
    }
}
