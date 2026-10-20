package edu.ums.web.ai;

import edu.ums.web.user.UmsPrincipal;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

public interface AiChatService {

    Reply chat(UmsPrincipal who, String message, List<HistoryTurn> history);

    Status status();

    record HistoryTurn(@Size(max = 12) String role, @Size(max = 1500) String text) {
    }

    record ChatRequest(@NotBlank @Size(max = 1000) String message, @Size(max = 12) List<HistoryTurn> history) {
    }

    record Reply(String reply, String source) {
    }

    record Status(String mode, String model) {
    }
}
