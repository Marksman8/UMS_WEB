package edu.ums.web.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import org.junit.jupiter.api.Test;

class StateMachineTest {

    enum S { A, B, C }

    private final StateMachine<S> machine = StateMachine.builder(S.class).allow(S.A, S.B).allow(S.B, S.C, S.A).build();

    @Test
    void everyPairIsDecidedByTheTable() {
        Set<String> legal = Set.of("A>B", "B>C", "B>A");
        for (S from : S.values()) {
            for (S to : S.values()) {
                assertEquals(legal.contains(from + ">" + to), machine.canMove(from, to), from + ">" + to);
            }
        }
    }

    @Test
    void terminalStateHasNoSuccessors() {
        assertTrue(machine.next(S.C).isEmpty());
    }

    @Test
    void requireThrowsTheGivenCodeForIllegalMove() {
        UmsException e = assertThrows(UmsException.class, () -> machine.require(S.A, S.C, ErrorCode.TERM_STATE, "no"));
        assertEquals(ErrorCode.TERM_STATE, e.code());
        assertFalse(machine.canMove(S.C, S.A));
    }

    @Test
    void randomWalkNeverLeavesTheTable() {
        java.util.Random rnd = new java.util.Random(42);
        S at = S.A;
        for (int i = 0; i < 10_000; i++) {
            S to = S.values()[rnd.nextInt(3)];
            if (machine.canMove(at, to)) {
                at = to;
            } else {
                assertThrows(UmsException.class, () -> machine.require(S.C, S.A, ErrorCode.TERM_STATE, null));
            }
        }
    }
}
