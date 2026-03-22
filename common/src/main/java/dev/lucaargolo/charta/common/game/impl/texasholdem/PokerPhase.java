package dev.lucaargolo.charta.common.game.impl.texasholdem;

/**
 * Represents the sequential phases of a Texas Hold'em hand.
 *
 * <p>Order: PREFLOP → FLOP → TURN → RIVER → SHOWDOWN</p>
 */
public enum PokerPhase {

    PREFLOP,
    FLOP,
    TURN,
    RIVER,
    SHOWDOWN;

    private static final PokerPhase[] VALUES = values();

    /**
     * Returns the phase corresponding to the given ordinal,
     * or {@link #PREFLOP} if the ordinal is out of range.
     */
    public static PokerPhase fromOrdinal(int ordinal) {
        return (ordinal >= 0 && ordinal < VALUES.length) ? VALUES[ordinal] : PREFLOP;
    }

    /** Returns {@code true} if this phase is a community-card round (post-preflop). */
    public boolean isPostFlop() {
        return this == FLOP || this == TURN || this == RIVER;
    }
}