package com.infinevo.payroll.proof;

import java.util.Objects;

/**
 * An immutable pair of two elements.
 *
 * @param <L> first element type
 * @param <R> second element type
 */
public record Pair<L, R>(L first, R second) {

    public Pair {
        Objects.requireNonNull(first, "first must not be null");
        Objects.requireNonNull(second, "second must not be null");
    }

    public static <L, R> Pair<L, R> of(L first, R second) {
        return new Pair<>(first, second);
    }

    public L getFirst() {
        return first;
    }

    public R getSecond() {
        return second;
    }
}
