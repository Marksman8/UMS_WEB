package edu.ums.web.academic;

import edu.ums.web.common.ErrorCode;
import edu.ums.web.common.StateMachine;
import edu.ums.web.common.UmsException;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Component;

import static edu.ums.web.academic.TermState.CLOSED;
import static edu.ums.web.academic.TermState.GRADING;
import static edu.ums.web.academic.TermState.IN_PROGRESS;
import static edu.ums.web.academic.TermState.REGISTRATION_OPEN;
import static edu.ums.web.academic.TermState.RESULTS_PUBLISHED;

// Complexity: time O(1) per check; space O(1) — five states, four transitions.

/** The term lifecycle as an explicit transition table: REGISTRATION_OPEN -> IN_PROGRESS -> GRADING -> RESULTS_PUBLISHED -> CLOSED. */
@Component
public class TermLifecycle {

    private final StateMachine<TermState> machine = StateMachine.builder(TermState.class)
            .allow(REGISTRATION_OPEN, IN_PROGRESS)
            .allow(IN_PROGRESS, GRADING)
            .allow(GRADING, RESULTS_PUBLISHED)
            .allow(RESULTS_PUBLISHED, CLOSED)
            .build();

    public Optional<TermState> successor(TermState from) {
        Set<TermState> next = machine.next(from);
        return next.stream().findFirst();
    }

    public TermState requireSuccessor(TermState from) {
        return successor(from).orElseThrow(() -> new UmsException(ErrorCode.TERM_STATE, "This term is already closed."));
    }

    public boolean canMove(TermState from, TermState to) {
        return machine.canMove(from, to);
    }

    public void requireMove(TermState from, TermState to) {
        machine.require(from, to, ErrorCode.TERM_STATE, "A term cannot move from " + from + " to " + to + ".");
    }
}
