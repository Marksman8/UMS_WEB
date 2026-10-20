package edu.ums.web.common;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

// Complexity: time O(1) per transition check (EnumSet lookup); space O(S + T) — states and declared transitions.

/**
 * Generic, immutable state machine: a fixed table of allowed (from, to) pairs. Lifecycles
 * (term, mark sheet) declare their table once and ask the machine before every change.
 */
public final class StateMachine<S extends Enum<S>> {

    private final Class<S> type;
    private final Map<S, Set<S>> allowed;

    private StateMachine(Class<S> type, Map<S, Set<S>> allowed) {
        this.type = type;
        this.allowed = allowed;
    }

    public static <S extends Enum<S>> Builder<S> builder(Class<S> type) {
        return new Builder<>(type);
    }

    public boolean canMove(S from, S to) {
        return allowed.get(from).contains(to);
    }

    public Set<S> next(S from) {
        return allowed.get(from);
    }

    /** Throws {@code code} when the move is not declared. */
    public void require(S from, S to, ErrorCode code, String detail) {
        if (!canMove(from, to)) {
            throw new UmsException(code, detail);
        }
    }

    public Class<S> type() {
        return type;
    }

    public static final class Builder<S extends Enum<S>> {
        private final Class<S> type;
        private final Map<S, Set<S>> table;

        private Builder(Class<S> type) {
            this.type = type;
            this.table = new EnumMap<>(type);
            for (S s : type.getEnumConstants()) {
                table.put(s, EnumSet.noneOf(type));
            }
        }

        @SafeVarargs
        public final Builder<S> allow(S from, S... to) {
            for (S target : to) {
                table.get(from).add(target);
            }
            return this;
        }

        public StateMachine<S> build() {
            Map<S, Set<S>> frozen = new EnumMap<>(type);
            table.forEach((k, v) -> frozen.put(k, Collections.unmodifiableSet(EnumSet.copyOf(v.isEmpty() ? EnumSet.noneOf(type) : v))));
            return new StateMachine<>(type, Collections.unmodifiableMap(frozen));
        }
    }
}
