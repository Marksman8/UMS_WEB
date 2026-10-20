package edu.ums.web.academic;

import static edu.ums.web.academic.TermState.CLOSED;
import static edu.ums.web.academic.TermState.GRADING;
import static edu.ums.web.academic.TermState.IN_PROGRESS;
import static edu.ums.web.academic.TermState.REGISTRATION_OPEN;
import static edu.ums.web.academic.TermState.RESULTS_PUBLISHED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import edu.ums.web.common.UmsException;
import org.junit.jupiter.api.Test;

class TermLifecycleTest {

    private final TermLifecycle lifecycle = new TermLifecycle();

    @Test
    void movesForwardOneStageAtATime() {
        assertEquals(IN_PROGRESS, lifecycle.requireSuccessor(REGISTRATION_OPEN));
        assertEquals(GRADING, lifecycle.requireSuccessor(IN_PROGRESS));
        assertEquals(RESULTS_PUBLISHED, lifecycle.requireSuccessor(GRADING));
        assertEquals(CLOSED, lifecycle.requireSuccessor(RESULTS_PUBLISHED));
    }

    @Test
    void cannotSkipOrGoBack() {
        assertFalse(lifecycle.canMove(REGISTRATION_OPEN, GRADING));
        assertFalse(lifecycle.canMove(RESULTS_PUBLISHED, GRADING));
        assertThrows(UmsException.class, () -> lifecycle.requireSuccessor(CLOSED));
    }
}
